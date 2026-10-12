#!/usr/bin/env bash
set -e

echo "=========================================="
echo " Running SynapseX Automated Test Suite   "
echo "=========================================="

echo "[1/3] Backend Unit & Integration Tests..."
cd backend
mvn clean test
cd ..

echo "[2/3] Frontend Unit & Component Tests..."
cd frontend
if [ -d "node_modules" ]; then
  npm test -- --run
else
  echo "Installing frontend dependencies..."
  npm install
  npm test -- --run
fi
cd ..

echo "[3/3] All test phases completed successfully!"
