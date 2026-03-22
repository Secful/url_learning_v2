#!/bin/bash
# Stops the running TrieHttpServer
PID=$(pgrep -f "salt.security.Main server")
if [ -n "$PID" ]; then
    kill "$PID"
    echo "Server stopped (PID $PID)"
else
    echo "No running server found"
fi
