#!/bin/bash
# Usage: ./run_server.sh [port]
# Default port: 8080
PORT=${1:-8080}
mvn -q package -DskipTests
java -cp target/url_learning_v2-1.0-SNAPSHOT.jar salt.security.Main server "$PORT"
