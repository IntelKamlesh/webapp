#!/bin/bash
# Quick start script for OpenShift Monitor Web Application

echo "=========================================="
echo "OpenShift Monitor Web Application"
echo "=========================================="
echo ""

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven is not installed or not in PATH"
    echo "Please install Maven from https://maven.apache.org/"
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 11 ]; then
    echo "ERROR: Java 11 or higher is required"
    echo "Current Java version: $(java -version 2>&1 | head -n 1)"
    exit 1
fi

echo "Java version: $(java -version 2>&1 | head -n 1 | cut -d'"' -f2)"
echo "Maven version: $(mvn -version | head -n 1)"
echo ""

# Check if monitoring script exists
SCRIPT_PATH="../openshift_intelligent_monitor_v8.sh"
if [ ! -f "$SCRIPT_PATH" ]; then
    echo "WARNING: Monitoring script not found at $SCRIPT_PATH"
    echo "Please ensure the script is in the correct location"
fi

echo "Starting web application..."
echo ""
echo "The application will be available at:"
echo "  http://localhost:8080"
echo ""
echo "Press Ctrl+C to stop the server"
echo ""
echo "=========================================="

# Start with Jetty (embedded server)
mvn clean jetty:run
