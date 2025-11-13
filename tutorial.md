


# how to run geoserver
1. Setting up database
	In the geoserver init folder, run:

	`docker compose up`

2. Migrate the schema to postgis db 
just run the java application:

`mvn quarkus:dev`

3. Add data from sample.json data file to the URI
POST 
http://localhost:9090/gasstations
`
{
    "name": "CH Xăng dầu 207 ( Hiện là CH của STS)",
    "address_old": "56 đường 3 tháng 2, P. Hưng Lợi ( Gần vòng xoay Đầu sấu)",
    "address_new": "56 đường 3 tháng 2, P. Tân An ( Gần vòng xoay Đầu sấu)",
    "owner": "Cty TNHH MTV xăng dầu Bảo Bảo ( Hiện là CH STS)",
    "supplier": "STS",
    "station_type": " CH STS",
    "nearest_petrolimex": {
        "name": "Petrolimex cửa hàng số 10",
        "distance_km": "0.4"
    },
    "estimated_sale": {
        "total": 0,
        "fuel": 0,
        "oil": 0
    },
    "pumps_quantity": {
        "fuel": 2,
        "oil": 2
    },
    "facade_length": 30,
    "dmn": ["PV OIL LUBE", "abc"],
    "other_services": [
        "Bãi rửa xe ô tô",
        "Nhà thuốc"
    ],
    "status": 2,
    "image": "test image",
    "notes": "test note",
    "coordinates": { "type": "Point", "coordinates": [10.014377641342394, 105.75818747210255] }
}

`

4. Setup geoserver and point it to the database
- access `http://127.0.0.1:8080/geoserver/`
- login
	- admin 
	- geoserver
- add new workspace
	- name: `station_pois`
	- namespace URI : `http://localhost:8080/geoserver/station_pois`
	- save
- add new store
	- choose `PostGIS (JNDI)`
	- Basic Store Info
		- Workspace: `station_pois`
		- Data Source Name: `station_pois`
	- Connection Parameters
		- dbtype: `postgis`
		- jndiReferenceName: `java:comp/env/jdbc/postgres` 
	- save
- create new layer
	- choose `Configure new SQL view...`
	- View Name: `station_pois`
	- patse this into SQL statement
```
SELECT 
    gs.gas_station_id,
    gs.station_name AS name,
    a.old_address,
    a.new_address,
    gs.owner_name AS owner,
    gs.supplier,
    gs.station_type,
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

```
- scroll down to Attributes
	- check on `Guess geometry type and srid`
	- click on `Refresh`
	- check the `Identifier` check box on `gas_station_id` row
	- choose `Save`
- scroll down to Bounding Boxes
	- click on `Compute from data`
	- click on `Compute from native bounds`
	- choose `Save`
- go back to homepage
	- on the left menu, go to Settings -> global
	- scroll down to `Service Response Settings`
	- set `Number of decimals (GML and GeoJSON output)` to 16
	- choose `Save`
---

# docker compose file

```yaml
version: "3.9"

services:
  geoserver-db:
    image: postgis/postgis:16-3.4
    container_name: geoserver-db
    restart: always
    environment:
      POSTGRES_USER: geoserver
      POSTGRES_PASSWORD: geoserver
      POSTGRES_DB: geodata
    volumes:
      - pg_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"

  geoserver:
    image: docker.osgeo.org/geoserver:2.27.2
    container_name: geoserver
    restart: always
    depends_on:
      - geoserver-db
    ports:
      - "8080:8080"
    environment:
      # Skip demo data for a clean setup
      SKIP_DEMO_DATA: "true"

      # (optional) Set admin credentials
      GEOSERVER_ADMIN_USER: admin
      GEOSERVER_ADMIN_PASSWORD: geoserver

      # Enable PostgreSQL JNDI connection (so GeoServer can find PostGIS easily)
      POSTGRES_JNDI_ENABLED: "true"
      POSTGRES_HOST: geoserver-db
      POSTGRES_PORT: 5432
      POSTGRES_DB: geodata
      POSTGRES_USERNAME: geoserver
      POSTGRES_PASSWORD: geoserver
      POSTGRES_JNDI_RESOURCE_NAME: "jdbc/postgres"

      # Allow CORS (optional)
      CORS_ENABLED: "true"

    volumes:
      # persistent GeoServer config/data
      - geoserver_data:/opt/geoserver_data/

volumes:
  geoserver_data:
  pg_data:
```