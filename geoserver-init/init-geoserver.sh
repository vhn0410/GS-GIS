#!/bin/sh
set -e

# --- Install dependencies ---
echo "Installing dependencies..."
apk add --no-cache curl jq

GEOSERVER_URL="http://geoserver:8080/geoserver"
AUTH="admin:geoserver"
WORKSPACE="station_pois"
NAMESPACE_URI="http://localhost:8080/geoserver/station_pois"
DATASTORE="station_pois"
LAYER="station_pois"
SRID="4326"

# --- Wait for GeoServer ---
echo "Waiting for GeoServer to be ready..."
MAX_RETRIES=60
RETRY_COUNT=0

until curl -u "$AUTH" -sf "$GEOSERVER_URL/rest/about/version.xml" > /dev/null 2>&1; do
  RETRY_COUNT=$((RETRY_COUNT + 1))
  if [ $RETRY_COUNT -ge $MAX_RETRIES ]; then
    echo "ERROR: GeoServer did not start in time"
    exit 1
  fi
  echo "Attempt $RETRY_COUNT/$MAX_RETRIES - waiting 5s..."
  sleep 5
done

echo "✓ GeoServer is ready!"
sleep 3

# ========================================
# STEP 1: Create Workspace
# ========================================
echo ""
echo "=========================================="
echo "STEP 1: Creating workspace '$WORKSPACE'"
echo "=========================================="

WORKSPACE_JSON=$(cat <<EOF
{
  "workspace": {
    "name": "$WORKSPACE"
  }
}
EOF
)

HTTP_CODE=$(curl -u "$AUTH" -s -o /tmp/response.txt -w "%{http_code}" \
  -X POST \
  -H "Content-Type: application/json" \
  -d "$WORKSPACE_JSON" \
  "$GEOSERVER_URL/rest/workspaces")

if [ "$HTTP_CODE" = "201" ]; then
  echo "✓ Workspace created successfully"
elif [ "$HTTP_CODE" = "409" ]; then
  echo "✓ Workspace already exists"
else
  echo "✗ Failed (HTTP $HTTP_CODE)"
  cat /tmp/response.txt
  exit 1
fi

# ========================================
# STEP 2: Set Namespace URI
# ========================================
echo ""
echo "=========================================="
echo "STEP 2: Setting namespace URI"
echo "=========================================="

# GeoServer tự động tạo namespace khi tạo workspace
# Nhưng cần update URI theo yêu cầu
NAMESPACE_JSON=$(cat <<EOF
{
  "namespace": {
    "prefix": "$WORKSPACE",
    "uri": "$NAMESPACE_URI"
  }
}
EOF
)

HTTP_CODE=$(curl -u "$AUTH" -s -o /tmp/response.txt -w "%{http_code}" \
  -X PUT \
  -H "Content-Type: application/json" \
  -d "$NAMESPACE_JSON" \
  "$GEOSERVER_URL/rest/namespaces/$WORKSPACE")

if [ "$HTTP_CODE" = "200" ]; then
  echo "✓ Namespace URI updated"
else
  echo "⚠ Namespace update returned HTTP $HTTP_CODE (may already be correct)"
fi

# ========================================
# STEP 3: Create PostGIS (JNDI) Store
# ========================================
echo ""
echo "=========================================="
echo "STEP 3: Creating PostGIS (JNDI) datastore"
echo "=========================================="

DATASTORE_JSON=$(cat <<EOF
{
  "dataStore": {
    "name": "$DATASTORE",
    "type": "PostGIS (JNDI)",
    "enabled": true,
    "workspace": {
      "name": "$WORKSPACE"
    },
    "connectionParameters": {
      "entry": [
        {"@key": "dbtype", "\$": "postgis"},
        {"@key": "jndiReferenceName", "\$": "java:comp/env/jdbc/postgres"},
        {"@key": "schema", "\$": "public"},
        {"@key": "Expose primary keys", "\$": "true"},
        {"@key": "Loose bbox", "\$": "true"}
      ]
    }
  }
}
EOF
)

HTTP_CODE=$(curl -u "$AUTH" -s -o /tmp/response.txt -w "%{http_code}" \
  -X POST \
  -H "Content-Type: application/json" \
  -d "$DATASTORE_JSON" \
  "$GEOSERVER_URL/rest/workspaces/$WORKSPACE/datastores")

if [ "$HTTP_CODE" = "201" ]; then
  echo "✓ Datastore created successfully"
elif [ "$HTTP_CODE" = "409" ]; then
  echo "✓ Datastore already exists"
else
  echo "✗ Failed (HTTP $HTTP_CODE)"
  cat /tmp/response.txt
  exit 1
fi

# ========================================
# STEP 4: Create SQL View Layer
# ========================================
echo ""
echo "=========================================="
echo "STEP 4: Creating SQL View layer"
echo "=========================================="

# SQL Query (exactly as provided)
SQL_QUERY='SELECT 
    gs.gas_station_id,
    gs.station_name AS name,
    a.old_address,
    a.new_address,
    gs.owner_name AS owner,
    gs.supplier,
    CASE gs.station_type
        WHEN '"'"'Sở hữu'"'"' THEN 0
        WHEN '"'"'Trực thuộc'"'"' THEN 1
        WHEN '"'"'Đại lý'"'"' THEN 2
        WHEN '"'"'Thương nhân nhận quyền'"'"' THEN 3
        WHEN '"'"'Khác'"'"' THEN 4
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
    COALESCE(dl.dmn, '"'"''"'"') AS dmn,
    COALESCE(sl.other_services, '"'"''"'"') AS other_services
FROM gas_station gs
LEFT JOIN address a ON a.address_id = gs.address_id
LEFT JOIN anchor an ON an.address_id = a.address_id
LEFT JOIN (
    SELECT
        ho.gas_station_id,
        SUM(CASE WHEN p.product_name = '"'"'Fuel'"'"' THEN ho.estimate_ouput ELSE 0 END) AS fuel,
        SUM(CASE WHEN p.product_name = '"'"'Oil'"'"' THEN ho.estimate_ouput ELSE 0 END) AS oil
    FROM has_output ho
    JOIN product p ON p.product_id = ho.product_id
    GROUP BY ho.gas_station_id
) s ON s.gas_station_id = gs.gas_station_id
LEFT JOIN (
    SELECT
        p.gas_station_id,
        SUM(CASE WHEN pr.product_name = '"'"'Fuel'"'"' THEN 1 ELSE 0 END) AS fuel,
        SUM(CASE WHEN pr.product_name = '"'"'Oil'"'"' THEN 1 ELSE 0 END) AS oil
    FROM pump p
    LEFT JOIN has_product hp ON hp.pump_id = p.pump_id
    LEFT JOIN product pr ON pr.product_id = hp.product_id
    GROUP BY p.gas_station_id
) pq ON pq.gas_station_id = gs.gas_station_id
LEFT JOIN (
    SELECT
        d.gas_station_id,
        string_agg(d.dmn_name, '"'"','"'"' ORDER BY d.dmn_name) AS dmn
    FROM dmn d
    GROUP BY d.gas_station_id
) dl ON dl.gas_station_id = gs.gas_station_id
LEFT JOIN (
    SELECT
        os.gas_station_id,
        string_agg(os.service_name, '"'"','"'"' ORDER BY os.service_name) AS other_services
    FROM other_services os
    GROUP BY os.gas_station_id
) sl ON sl.gas_station_id = gs.gas_station_id
WHERE an.geometry IS NOT NULL'

# Create JSON using jq for proper escaping
LAYER_JSON=$(jq -n \
  --arg name "$LAYER" \
  --arg sql "$SQL_QUERY" \
  --arg srid "$SRID" \
  '{
    featureType: {
      name: $name,
      nativeName: $name,
      title: "Gas Station POIs",
      srs: ("EPSG:" + $srid),
      projectionPolicy: "FORCE_DECLARED",
      enabled: true,
      metadata: {
        entry: {
          "@key": "JDBC_VIRTUAL_TABLE",
          virtualTable: {
            name: $name,
            sql: $sql,
            escapeSql: false,
            keyColumn: "gas_station_id",
            geometry: {
              name: "geometry",
              type: "Geometry",
              srid: ($srid | tonumber)
            }
          }
        }
      },
      attributes: {
        attribute: [
          {name: "gas_station_id", minOccurs: 0, maxOccurs: 1, nillable: true, binding: "java.lang.Long"},
          {name: "name", minOccurs: 0, maxOccurs: 1, nillable: true, binding: "java.lang.String"},
          {name: "old_address", minOccurs: 0, maxOccurs: 1, nillable: true, binding: "java.lang.String"},
          {name: "new_address", minOccurs: 0, maxOccurs: 1, nillable: true, binding: "java.lang.String"},
          {name: "owner", minOccurs: 0, maxOccurs: 1, nillable: true, binding: "java.lang.String"},
          {name: "supplier", minOccurs: 0, maxOccurs: 1, nillable: true, binding: "java.lang.String"},
          {name: "station_type", minOccurs: 0, maxOccurs: 1, nillable: true, binding: "java.lang.Integer"},
          {name: "facade_length", minOccurs: 0, maxOccurs: 1, nillable: true, binding: "java.lang.Double"},
          {name: "status", minOccurs: 0, maxOccurs: 1, nillable: true, binding: "java.lang.Integer"},
          {name: "image", minOccurs: 0, maxOccurs: 1, nillable: true, binding: "java.lang.String"},
          {name: "nearest_petrolimex_name", minOccurs: 0, maxOccurs: 1, nillable: true, binding: "java.lang.String"},
          {name: "nearest_petrolimex_distance", minOccurs: 0, maxOccurs: 1, nillable: true, binding: "java.lang.Double"},
          {name: "notes", minOccurs: 0, maxOccurs: 1, nillable: true, binding: "java.lang.String"},
          {name: "geometry", minOccurs: 0, maxOccurs: 1, nillable: true, binding: "org.locationtech.jts.geom.Geometry"},
          {name: "estimated_sale_fuel", minOccurs: 0, maxOccurs: 1, nillable: true, binding: "java.lang.Double"},
          {name: "estimated_sale_oil", minOccurs: 0, maxOccurs: 1, nillable: true, binding: "java.lang.Double"},
          {name: "estimated_sale_total", minOccurs: 0, maxOccurs: 1, nillable: true, binding: "java.lang.Double"},
          {name: "pumps_fuel", minOccurs: 0, maxOccurs: 1, nillable: true, binding: "java.lang.Double"},
          {name: "pumps_oil", minOccurs: 0, maxOccurs: 1, nillable: true, binding: "java.lang.Double"},
          {name: "dmn", minOccurs: 0, maxOccurs: 1, nillable: true, binding: "java.lang.String"},
          {name: "other_services", minOccurs: 0, maxOccurs: 1, nillable: true, binding: "java.lang.String"}
        ]
      }
    }
  }')

echo "$LAYER_JSON" > /tmp/layer.json

HTTP_CODE=$(curl -u "$AUTH" -s -o /tmp/response.txt -w "%{http_code}" \
  -X POST \
  -H "Content-Type: application/json" \
  -d @/tmp/layer.json \
  "$GEOSERVER_URL/rest/workspaces/$WORKSPACE/datastores/$DATASTORE/featuretypes")

if [ "$HTTP_CODE" = "201" ]; then
  echo "✓ SQL View layer created successfully"
elif [ "$HTTP_CODE" = "409" ]; then
  echo "✓ Layer already exists"
else
  echo "✗ Failed (HTTP $HTTP_CODE)"
  cat /tmp/response.txt
  exit 1
fi

# ========================================
# STEP 5: Compute Bounding Boxes
# ========================================
echo ""
echo "=========================================="
echo "STEP 5: Computing bounding boxes"
echo "=========================================="

# Compute from data (native bounds)
echo "Computing native bounding box from data..."
HTTP_CODE=$(curl -u "$AUTH" -s -o /dev/null -w "%{http_code}" \
  -X PUT \
  "$GEOSERVER_URL/rest/workspaces/$WORKSPACE/datastores/$DATASTORE/featuretypes/$LAYER?recalculate=nativebbox")
echo "  Native bbox: HTTP $HTTP_CODE"

# Compute from native bounds (lat/lon bounds)
echo "Computing lat/lon bounding box..."
HTTP_CODE=$(curl -u "$AUTH" -s -o /dev/null -w "%{http_code}" \
  -X PUT \
  "$GEOSERVER_URL/rest/workspaces/$WORKSPACE/datastores/$DATASTORE/featuretypes/$LAYER?recalculate=latlonbbox")
echo "  Lat/lon bbox: HTTP $HTTP_CODE"

# ========================================
# STEP 6: Set Global Number of Decimals
# ========================================
echo ""
echo "=========================================="
echo "STEP 6: Setting global number of decimals to 16"
echo "=========================================="

SETTINGS_JSON=$(cat <<EOF
{
  "global": {
    "settings": {
      "numDecimals": 16
    }
  }
}
EOF
)

HTTP_CODE=$(curl -u "$AUTH" -s -o /tmp/response.txt -w "%{http_code}" \
  -X PUT \
  -H "Content-Type: application/json" \
  -d "$SETTINGS_JSON" \
  "$GEOSERVER_URL/rest/settings")

if [ "$HTTP_CODE" = "200" ]; then
  echo "✓ Global settings updated"
else
  echo "⚠ Settings update returned HTTP $HTTP_CODE"
  cat /tmp/response.txt
fi

# ========================================
# COMPLETION
# ========================================
echo ""
echo "=========================================="
echo "✓✓✓ GeoServer Setup Complete! ✓✓✓"
echo "=========================================="
echo ""
echo "Configuration Summary:"
echo "  Workspace: $WORKSPACE"
echo "  Namespace URI: $NAMESPACE_URI"
echo "  Datastore: $DATASTORE (PostGIS JNDI)"
echo "  Layer: $LAYER (SQL View)"
echo "  SRID: EPSG:$SRID"
echo "  Decimals: 16"
echo ""
echo "Access your services:"
echo "  WMS GetCapabilities:"
echo "    $GEOSERVER_URL/wms?service=WMS&request=GetCapabilities"
echo ""
echo "  WFS GetCapabilities:"
echo "    $GEOSERVER_URL/wfs?service=WFS&request=GetCapabilities"
echo ""
echo "  Layer Preview:"
echo "    $GEOSERVER_URL/web/?wicket:bookmarkablePage=:org.geoserver.web.demo.MapPreviewPage"
echo ""
echo "=========================================="