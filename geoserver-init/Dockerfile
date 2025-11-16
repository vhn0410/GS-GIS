FROM python:3.11-slim

WORKDIR /app

# Install dependencies
RUN pip install --no-cache-dir requests

# Copy script
COPY setup_geoserver.py .

# Set environment variables with defaults
ENV GEOSERVER_URL="http://geoserver:8080/geoserver/rest"
ENV GEOSERVER_USER="admin"
ENV GEOSERVER_PASSWORD="geoserver"

# Run script
CMD ["python", "setup_geoserver.py"]