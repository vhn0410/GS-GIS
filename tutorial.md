
# how to run geoserver
1. Setting up database
	In this folder, run:

	`docker compose up`

	`docker cp ./schema.sql geoserver-db:/schema.sql`

	`docker exec -it geoserver-db bash`

	`psql -U geoserver -f schema.sql geodata`

# psql -U <user> -f <file.sql> <database>

2. Setup geoserver and point it to the database
- access `http://127.0.0.1:8080/geoserver/`
- login
	- admin 
	- geoserver
- add new workspace
	- name: `land_planning`
	- namespace URI : `http://localhost:8080/geoserver/land-planning`
	- save
- add new store
	- choose `PostGIS (JNDI)`
	- Basic Store Info
		- Workspace: `land_planning`
		- Data Source Name: `land_planning`
	- Connection Parameters
		- dbtype: `postgis`
		- jndiReferenceName: `java:comp/env/jdbc/postgres` 
	- save
- create new layer
	- choose `Configure new SQL view...`
	- View Name: `land_planning`
	- patse this into SQL statement
```
SELECT
    p.id AS plot_id,
    p.name AS plot_name,
    p.area AS plot_area,
    p.owner AS plot_owner,
    pr.name AS province_name,
    d.name AS district_name,
    COALESCE(
        JSON_AGG(
            DISTINCT JSONB_BUILD_OBJECT(
                'name', lt.name,
                'code', lt.code,
                'orders', lt.orders
            )
        ) FILTER (WHERE lt.id IS NOT NULL),
        '[]'
    ) AS landtypes,
    p.geometry AS the_geom,
    CASE
        WHEN proj.id IS NOT NULL THEN
            JSONB_BUILD_OBJECT(
                'id', proj.id,
                'title', proj.title,
                'start_date', proj.start_date,
                'end_date', proj.end_date
            )
        ELSE NULL
    END AS project
FROM plot p
JOIN province pr ON p.province_id = pr.id
LEFT JOIN district d ON p.district_id = d.id
LEFT JOIN is_type it ON it.plot_id = p.id
LEFT JOIN land_type lt ON lt.id = it.land_type_id
LEFT JOIN project proj ON proj.plot_id = p.id
GROUP BY p.id, p.name, p.area, p.owner, pr.name, d.name, p.geometry, proj.id, proj.title, proj.start_date, proj.end_date

```
- scroll down to Attributes
	- check on `Guess geometry type and srid`
	- click on `Refresh`
	- check the `Identifier` check box on `plot_id` row
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