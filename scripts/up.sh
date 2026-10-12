#!/usr/bin/env bash
set -e

echo "Starting SynapseX infrastructure & services..."
docker compose up --build -d

echo "Waiting for services to become healthy..."
docker compose ps
echo "SynapseX is accessible at:"
echo "  Frontend:   http://localhost:3000"
echo "  Backend:    http://localhost:8080/swagger-ui.html"
echo "  Prometheus: http://localhost:9090"
echo "  Grafana:    http://localhost:3001 (admin/synapsex_admin)"
