#!/bin/bash

# Run Redis benchmark locally with Docker Redis

set -e

echo "=== Starting Redis container ==="
docker run -d --name e4s-redis -p 6379:6379 redis:7-alpine redis-server --maxmemory 256mb

echo "Waiting for Redis to be ready..."
sleep 2
until docker exec e4s-redis redis-cli ping | grep -q PONG; do
    sleep 1
done
echo "Redis is ready!"

echo ""
echo "=== Building application ==="
mvn clean package test-compile -DskipTests -q

echo ""
echo "=== Running benchmark ==="
REDIS_HOST=localhost REDIS_PORT=6379 mvn exec:java@run-benchmark -q

echo ""
echo "=== Stopping Redis container ==="
docker stop e4s-redis
docker rm e4s-redis

echo "Done!"
