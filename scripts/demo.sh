#!/usr/bin/env bash
set -e

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
echo "=========================================="
echo " SynapseX Automated End-to-End Demo      "
echo "=========================================="

echo "1. Authenticating as seeded engineer..."
LOGIN_RESP=$(curl -s -X POST "$BACKEND_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@synapsex.io","password":"Password123!"}')

ACCESS_TOKEN=$(echo "$LOGIN_RESP" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
if [ -z "$ACCESS_TOKEN" ]; then
  echo "Login failed. Ensure backend is running."
  echo "Response: $LOGIN_RESP"
  exit 1
fi
echo "Authenticated successfully. Token acquired."

echo "2. Triggering Database Connection Pool Exhaustion Simulation..."
SIM_RESP=$(curl -s -X POST "$BACKEND_URL/api/simulations/start" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"scenario":"DATABASE_CONNECTION_POOL_EXHAUSTION","durationSeconds":60,"seed":42}')
echo "Simulation started: $SIM_RESP"

echo "3. Polling for generated incidents..."
sleep 3
INCIDENTS=$(curl -s -X GET "$BACKEND_URL/api/incidents" \
  -H "Authorization: Bearer $ACCESS_TOKEN")
echo "Active Incidents: $INCIDENTS"

echo "Demo workflow completed. View UI at http://localhost:3000"
