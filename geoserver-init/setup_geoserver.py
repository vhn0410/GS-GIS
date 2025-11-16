import requests
from requests.auth import HTTPBasicAuth
import json
import os
import time
import sys

# -----------------------------
# GeoServer configuration from environment
# -----------------------------
GEOSERVER_URL = os.getenv("GEOSERVER_URL", "http://geoserver:8080/geoserver/rest")
USERNAME = os.getenv("GEOSERVER_USER", "admin")
PASSWORD = os.getenv("GEOSERVER_PASSWORD", "geoserver")

WORKSPACE = "station_pois"
NAMESPACE_URI = "http://localhost:8080/geoserver/station_pois"
DATASTORE = "station_pois"
LAYER = "station_pois"
GEOM_COLUMN = "geometry"
SRID = 4326

HEADERS_XML = {"Content-Type": "application/xml"}
HEADERS_JSON = {"Content-Type": "application/json"}

auth = HTTPBasicAuth(USERNAME, PASSWORD)

# SQL query from tutorial
SQL_QUERY = """
SELECT 
    gs.gas_station_id,
    gs.station_name AS name,
    a.old_address,
    a.new_address,
    gs.owner_name AS owner,
    gs.supplier,
    -- station_type as integer
    CASE gs.station_type
        WHEN 'Sở hữu' THEN 0
        WHEN 'Trực thuộc' THEN 1
        WHEN 'Đại lý' THEN 2
        WHEN 'Thương nhân nhận quyền' THEN 3
        WHEN 'Khác' THEN 4
    END AS station_type,
    gs.facade_length,
    gs.status,
    gs.image,
    gs.nearest_petrolimex_name,
    gs.nearest_petrolimex_distance,
    gs.note AS notes,
    an.geometry,  
    COALESCE(s.fuel, 0) AS estimated_sale_fuel,
    COALESCE(s.oil, 0) AS estimated_sale_oil,
    COALESCE(s.fuel, 0) + COALESCE(s.oil, 0) AS estimated_sale_total,
    COALESCE(pq.fuel, 0) AS pumps_fuel,
    COALESCE(pq.oil, 0) AS pumps_oil,
    -- Chuyển từ array_agg sang string_agg để GeoServer nhận
    COALESCE(dl.dmn, '') AS dmn,
    COALESCE(sl.other_services, '') AS other_services
FROM gas_station gs
LEFT JOIN address a ON a.address_id = gs.address_id
LEFT JOIN anchor an ON an.address_id = a.address_id
LEFT JOIN (
    SELECT
        ho.gas_station_id,
        SUM(CASE WHEN p.product_name = 'Fuel' THEN ho.estimate_ouput ELSE 0 END) AS fuel,
        SUM(CASE WHEN p.product_name = 'Oil' THEN ho.estimate_ouput ELSE 0 END) AS oil
    FROM has_output ho
    JOIN product p ON p.product_id = ho.product_id
    GROUP BY ho.gas_station_id
) s ON s.gas_station_id = gs.gas_station_id
LEFT JOIN (
    SELECT
        p.gas_station_id,
        SUM(CASE WHEN pr.product_name = 'Fuel' THEN 1 ELSE 0 END) AS fuel,
        SUM(CASE WHEN pr.product_name = 'Oil' THEN 1 ELSE 0 END) AS oil
    FROM pump p
    LEFT JOIN has_product hp ON hp.pump_id = p.pump_id
    LEFT JOIN product pr ON pr.product_id = hp.product_id
    GROUP BY p.gas_station_id
) pq ON pq.gas_station_id = gs.gas_station_id
-- dmn
LEFT JOIN (
    SELECT
        d.gas_station_id,
        string_agg(d.dmn_name, ',' ORDER BY d.dmn_name) AS dmn
    FROM dmn d
    GROUP BY d.gas_station_id
) dl ON dl.gas_station_id = gs.gas_station_id
-- other_services
LEFT JOIN (
    SELECT
        os.gas_station_id,
        string_agg(os.service_name, ',' ORDER BY os.service_name) AS other_services
    FROM other_services os
    GROUP BY os.gas_station_id
) sl ON sl.gas_station_id = gs.gas_station_id
WHERE an.geometry IS NOT NULL
"""

def wait_for_geoserver(max_retries=30, delay=5):
    """Wait for GeoServer to be ready"""
    print("🔄 Waiting for GeoServer to be ready...")
    for i in range(max_retries):
        try:
            resp = requests.get(f"{GEOSERVER_URL}/about/version.json", 
                              auth=auth, 
                              timeout=5)
            if resp.status_code == 200:
                print("✅ GeoServer is ready!")
                return True
        except requests.exceptions.RequestException as e:
            print(f"⏳ Attempt {i+1}/{max_retries}: GeoServer not ready yet... ({e})")
            time.sleep(delay)
    
    print("❌ GeoServer did not become ready in time")
    return False

def main():
    print("=" * 70)
    print("GeoServer Auto-Setup Script - Docker Version")
    print("=" * 70)
    print(f"Target GeoServer: {GEOSERVER_URL}")
    print(f"Username: {USERNAME}")
    print("=" * 70)

    # Wait for GeoServer to be ready
    if not wait_for_geoserver():
        sys.exit(1)

    # Add a small delay to ensure GeoServer is fully initialized
    print("\n⏳ Waiting 10 seconds for GeoServer to fully initialize...")
    time.sleep(10)

    # -----------------------------
    # Step 1: Create workspace
    # -----------------------------
    print("\n[STEP 1] Creating workspace...")
    workspace_data = f"<workspace><name>{WORKSPACE}</name></workspace>"
    workspace_url = f"{GEOSERVER_URL}/workspaces"

    try:
        resp = requests.post(workspace_url, data=workspace_data, headers=HEADERS_XML, auth=auth, timeout=30)
        if resp.status_code == 201:
            print(f"✅ Workspace '{WORKSPACE}' created successfully.")
        elif resp.status_code == 409:
            print(f"⚠️  Workspace '{WORKSPACE}' already exists.")
        else:
            print(f"❌ Failed to create workspace: {resp.status_code}")
            print(resp.text)
            sys.exit(1)
    except requests.exceptions.RequestException as e:
        print(f"❌ Error creating workspace: {e}")
        sys.exit(1)

    # -----------------------------
    # Step 2: Update namespace URI
    # -----------------------------
    print("\n[STEP 2] Updating namespace URI...")
    namespace_data = f"<namespace><prefix>{WORKSPACE}</prefix><uri>{NAMESPACE_URI}</uri></namespace>"
    namespace_url = f"{GEOSERVER_URL}/namespaces/{WORKSPACE}"

    try:
        resp = requests.put(namespace_url, data=namespace_data, headers=HEADERS_XML, auth=auth, timeout=30)
        if resp.status_code in [200, 201]:
            print(f"✅ Namespace URI set to '{NAMESPACE_URI}'")
        else:
            print(f"❌ Failed to update namespace URI: {resp.status_code}")
            print(resp.text)
            sys.exit(1)
    except requests.exceptions.RequestException as e:
        print(f"❌ Error updating namespace: {e}")
        sys.exit(1)

    # -----------------------------
    # Step 3: Create PostGIS (JNDI) datastore
    # -----------------------------
    print("\n[STEP 3] Creating PostGIS (JNDI) datastore...")
    datastore_data = f"""
<dataStore>
  <name>{DATASTORE}</name>
  <type>PostGIS (JNDI)</type>
  <enabled>true</enabled>
  <workspace>
    <name>{WORKSPACE}</name>
  </workspace>
  <connectionParameters>
    <entry key="dbtype">postgis</entry>
    <entry key="jndiReferenceName">java:comp/env/jdbc/postgres</entry>
  </connectionParameters>
</dataStore>
"""
    datastore_url = f"{GEOSERVER_URL}/workspaces/{WORKSPACE}/datastores"

    try:
        resp = requests.post(datastore_url, data=datastore_data, headers=HEADERS_XML, auth=auth, timeout=30)
        if resp.status_code == 201:
            print(f"✅ Datastore '{DATASTORE}' created successfully!")
        elif resp.status_code == 409:
            print(f"⚠️  Datastore '{DATASTORE}' already exists.")
        else:
            print(f"❌ Failed to create datastore: {resp.status_code}")
            print(resp.text)
            sys.exit(1)
    except requests.exceptions.RequestException as e:
        print(f"❌ Error creating datastore: {e}")
        sys.exit(1)

    # -----------------------------
    # Step 4: Create SQL View Layer
    # -----------------------------
    print("\n[STEP 4] Creating SQL view layer...")

    # Create the feature type with SQL view
    featuretype_data = f"""
<featureType>
  <name>{LAYER}</name>
  <nativeName>{LAYER}</nativeName>
  <title>Station POIs</title>
  <enabled>true</enabled>
  <srs>EPSG:{SRID}</srs>
  <metadata>
    <entry key="JDBC_VIRTUAL_TABLE">
      <virtualTable>
        <name>{LAYER}</name>
        <sql>{SQL_QUERY}</sql>
        <escapeSql>false</escapeSql>
        <geometry>
          <name>{GEOM_COLUMN}</name>
          <type>Point</type>
          <srid>{SRID}</srid>
        </geometry>
        <keyColumn>gas_station_id</keyColumn>
      </virtualTable>
    </entry>
  </metadata>
</featureType>
"""

    featuretype_url = f"{GEOSERVER_URL}/workspaces/{WORKSPACE}/datastores/{DATASTORE}/featuretypes"

    try:
        resp = requests.post(featuretype_url, data=featuretype_data, headers=HEADERS_XML, auth=auth, timeout=30)
        if resp.status_code == 201:
            print(f"✅ Layer '{LAYER}' created successfully!")
        elif resp.status_code == 409:
            print(f"⚠️  Layer '{LAYER}' already exists. Trying to update...")
            # Try to update the existing layer
            update_url = f"{GEOSERVER_URL}/workspaces/{WORKSPACE}/datastores/{DATASTORE}/featuretypes/{LAYER}"
            resp = requests.put(update_url, data=featuretype_data, headers=HEADERS_XML, auth=auth, timeout=30)
            if resp.status_code in [200, 201]:
                print(f"✅ Layer '{LAYER}' updated successfully!")
            else:
                print(f"❌ Failed to update layer: {resp.status_code}")
                print(resp.text)
        else:
            print(f"❌ Failed to create layer: {resp.status_code}")
            print(resp.text)
            sys.exit(1)
    except requests.exceptions.RequestException as e:
        print(f"❌ Error creating layer: {e}")
        sys.exit(1)

    # -----------------------------
    # Step 5: Recalculate bounding boxes
    # -----------------------------
    print("\n[STEP 5] Recalculating bounding boxes...")
    layer_url = f"{GEOSERVER_URL}/workspaces/{WORKSPACE}/datastores/{DATASTORE}/featuretypes/{LAYER}"

    try:
        # Get current layer configuration
        resp = requests.get(layer_url, headers={"Accept": "application/json"}, auth=auth, timeout=30)
        if resp.status_code == 200:
            layer_config = resp.json()
            
            # Update with recalculate parameter
            layer_config['featureType']['enabled'] = True
            
            # PUT request with recalculate parameter to compute bounds
            recalc_url = f"{layer_url}?recalculate=nativebbox,latlonbbox"
            resp = requests.put(recalc_url, 
                              data=json.dumps(layer_config), 
                              headers=HEADERS_JSON, 
                              auth=auth,
                              timeout=30)
            if resp.status_code in [200, 201]:
                print("✅ Bounding boxes recalculated successfully!")
            else:
                print(f"⚠️  Could not recalculate bounding boxes: {resp.status_code}")
                print(resp.text)
        else:
            print(f"⚠️  Could not retrieve layer configuration: {resp.status_code}")
    except requests.exceptions.RequestException as e:
        print(f"⚠️  Error recalculating bounding boxes: {e}")

    # -----------------------------
    # Step 6: Update global settings
    # -----------------------------
    print("\n[STEP 6] Updating global settings (GeoJSON decimals to 16)...")
    global_url = f"{GEOSERVER_URL}/settings"

    try:
        # Get current settings
        resp = requests.get(global_url, headers={"Accept": "application/json"}, auth=auth, timeout=30)
        if resp.status_code == 200:
            settings = resp.json()
            
            # Update numDecimals
            if 'global' not in settings:
                settings['global'] = {}
            settings['global']['numDecimals'] = 16
            
            # Update settings
            resp = requests.put(global_url, 
                              data=json.dumps(settings), 
                              headers=HEADERS_JSON, 
                              auth=auth,
                              timeout=30)
            if resp.status_code in [200, 201]:
                print("✅ Global settings updated (numDecimals = 16)")
            else:
                print(f"⚠️  Could not update global settings: {resp.status_code}")
                print(resp.text)
        else:
            print(f"⚠️  Could not retrieve global settings: {resp.status_code}")
    except requests.exceptions.RequestException as e:
        print(f"⚠️  Error updating global settings: {e}")

    # -----------------------------
    # Summary
    # -----------------------------
    print("\n" + "=" * 70)
    print("✅ Setup Complete!")
    print("=" * 70)
    print(f"\nYou can now access your layer at:")
    print(f"  WMS: http://localhost:8080/geoserver/{WORKSPACE}/wms")
    print(f"  WFS: http://localhost:8080/geoserver/{WORKSPACE}/wfs")
    print(f"\nLayer preview:")
    print(f"  http://localhost:8080/geoserver/web/?wicket:bookmarkablePage=:org.geoserver.web.demo.MapPreviewPage")
    print("=" * 70)

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"\n❌ Fatal error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)