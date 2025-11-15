#!/bin/bash

echo "=== Building Quarkus Application ==="
./mvnw clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Maven build failed!"
    exit 1
fi

echo "✅ Build successful!"

echo "=== Starting Docker Compose ==="
docker-compose down
docker-compose up --build -d

echo "=== Waiting for services to be ready ==="
sleep 10

echo "=== Service Status ==="
docker-compose ps

echo ""
echo "✅ All services started!"
echo ""
echo "📍 Services URLs:"
echo "   - GeoServer:     http://localhost:8080/geoserver"
echo "   - Quarkus App:   http://localhost:9090"
echo "   - Swagger UI:    http://localhost:9090/swagger-ui"
echo "   - PostgreSQL:    localhost:5432"
echo ""
echo "🔐 Credentials:"
echo "   - GeoServer:     admin / geoserver"
echo "   - PostgreSQL:    geoserver / geoserver"
echo ""
echo "📋 Useful commands:"
echo "   - View logs:     docker-compose logs -f quarkus-app"
echo "   - Stop all:      docker-compose down"
echo "   - Restart app:   docker-compose restart quarkus-app"