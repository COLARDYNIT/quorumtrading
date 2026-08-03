#!/bin/bash
set -e

# QuorumTrading - LLM Integration Test Runner
# This script runs integration tests with real OpenRouter API calls

echo "======================================"
echo "QuorumTrading LLM Integration Test Runner"
echo "======================================"
echo ""

# Check if API key is set
if [ -z "$OPENROUTER_API_KEY" ]; then
    echo "❌ ERROR: OPENROUTER_API_KEY environment variable is not set"
    echo ""
    echo "To run integration tests, you need an OpenRouter API key:"
    echo "  1. Sign up at https://openrouter.ai/"
    echo "  2. Generate an API key"
    echo "  3. Export the key:"
    echo "       export OPENROUTER_API_KEY=\"sk-or-v1-your-key-here\""
    echo ""
    exit 1
fi

echo "✅ OpenRouter API key detected"

# Check if PostgreSQL is running
echo "🔍 Checking PostgreSQL..."
if ! docker ps | grep -q postgres; then
    echo "⚠️  PostgreSQL not running. Starting services..."
    cd "$(dirname "$0")/.."
    docker-compose up -d postgres redis
    echo "⏳ Waiting 10 seconds for PostgreSQL to initialize..."
    sleep 10
else
    echo "✅ PostgreSQL is running"
fi

# Check if Redis is running
echo "🔍 Checking Redis..."
if ! docker ps | grep -q redis; then
    echo "⚠️  Redis not running. Starting Redis..."
    cd "$(dirname "$0")/.."
    docker-compose up -d redis
    echo "⏳ Waiting 5 seconds for Redis to initialize..."
    sleep 5
else
    echo "✅ Redis is running"
fi

echo ""
echo "======================================"
echo "Running Integration Tests"
echo "======================================"
echo ""
echo "⚠️  NOTE: Tests use FREE OpenRouter models"
echo "   No charges will be incurred"
echo ""
echo "Expected duration: 3-5 minutes"
echo "Using models: qwen/qwen-2.5-coder-32b-instruct:free"
echo ""

cd "$(dirname "$0")/../quorumtrading-backend"

# Run the tests
./mvnw test -Dtest=LLMOrchestratorIntegrationTest

TEST_EXIT_CODE=$?

echo ""
echo "======================================"
if [ $TEST_EXIT_CODE -eq 0 ]; then
    echo "✅ All integration tests PASSED"
else
    echo "❌ Integration tests FAILED"
    echo "   Check logs above for details"
fi
echo "======================================"

exit $TEST_EXIT_CODE
