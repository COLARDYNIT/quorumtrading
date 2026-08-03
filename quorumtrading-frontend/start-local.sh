#!/bin/bash
# Start frontend in local profile mode
# Usage: ./start-local.sh

set -e

echo "Starting frontend with local profile..."
echo "Port: 3001"
echo "API URL: http://localhost:8085/api/v1"
echo ""

# Export local profile environment variables (backend local profile listens on 8085 —
# see docs/RUN_MODES.md)
export NEXT_PUBLIC_API_URL=http://localhost:8085/api/v1
export NEXT_PUBLIC_WS_URL=ws://localhost:8085/ws/stream

# Start Next.js on port 3001
# Environment variables override .env.local
npm run dev -- -p 3001
