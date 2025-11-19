# OpenShift Intelligent Monitor - Web Application

A Java servlet-based web application for running OpenShift monitoring commands with a user-friendly interface.

## Features

- **Tabbed Interface**: Browse monitoring groups (A-T) with detailed descriptions
- **Selective Execution**: Choose which monitoring groups to run
- **Two Modes**:
  - **Actionable Mode**: Shows only problems and issues (default)
  - **Verbose Mode**: Shows complete output for archival
- **Report Management**: View and access previously generated HTML reports
- **Real-time Execution**: Run monitoring scripts and get instant feedback

## Prerequisites

- Java 11 or higher
- Maven 3.6+
- Apache Tomcat 9+ or any Java Servlet 4.0 compatible container
- OpenShift CLI (`oc`) configured and logged in
- The monitoring script (`openshift_intelligent_monitor_v8.sh`) in the parent directory

## Project Structure

```
webapp/
├── pom.xml                                 # Maven build configuration
├── src/
│   └── main/
│       ├── java/
│       │   └── com/openshift/monitor/
│       │       ├── MonitorServlet.java     # Main servlet handling API requests
│       │       └── CorsFilter.java         # CORS filter for development
│       └── webapp/
│           ├── index.html                  # Main HTML page
│           ├── css/
│           │   └── style.css              # Stylesheet
│           ├── js/
│           │   └── app.js                 # Frontend JavaScript
│           └── WEB-INF/
│               └── web.xml                # Web application configuration
```

## Installation & Setup

### Option 1: Using Maven with Jetty (Quick Start)

1. Navigate to the webapp directory:
   ```bash
   cd webapp
   ```

2. Build and run with Jetty:
   ```bash
   mvn clean package jetty:run
   ```

3. Open your browser and go to:
   ```
   http://localhost:8080
   ```

### Option 2: Using Maven with Tomcat Plugin

1. Navigate to the webapp directory:
   ```bash
   cd webapp
   ```

2. Build and run with Tomcat:
   ```bash
   mvn clean package tomcat7:run
   ```

3. Open your browser and go to:
   ```
   http://localhost:8080
   ```

### Option 3: Deploy to External Tomcat

1. Build the WAR file:
   ```bash
   cd webapp
   mvn clean package
   ```

2. The WAR file will be created at:
   ```
   target/openshift-monitor.war
   ```

3. Deploy to Tomcat:
   - Copy the WAR file to your Tomcat's `webapps` directory:
     ```bash
     cp target/openshift-monitor.war $TOMCAT_HOME/webapps/
     ```
   - Start Tomcat:
     ```bash
     $TOMCAT_HOME/bin/startup.sh
     ```

4. Access the application:
   ```
   http://localhost:8080/openshift-monitor
   ```

### Option 4: Deploy to WildFly/JBoss

1. Build the WAR file:
   ```bash
   cd webapp
   mvn clean package
   ```

2. Deploy to WildFly:
   ```bash
   cp target/openshift-monitor.war $WILDFLY_HOME/standalone/deployments/
   ```

3. Access the application:
   ```
   http://localhost:8080/openshift-monitor
   ```

## Usage

### 1. Select Monitoring Groups

- The interface displays all available monitoring groups (A-T) as tabs
- Click on any tab to view details about that monitoring group
- Use the checkbox to include/exclude groups from the monitoring run

**Quick Actions:**
- **Select All**: Selects all monitoring groups
- **Deselect All**: Clears all selections

### 2. Choose Mode

- **Actionable Mode**: Generates a focused report showing only problems (faster)
- **Verbose Mode**: Generates a complete report with all output (comprehensive)

### 3. Run Monitor

1. Click the "Run Monitor" button
2. The script will execute in the background (may take several minutes)
3. A status message will appear when complete
4. Click "View Report" to see the generated HTML report

### 4. View Reports

- All generated reports are listed in the "Recent Reports" section
- Click "View Report" to open any report in a new tab
- Reports are sorted by creation time (newest first)

## Monitoring Groups

| Group | Description | Commands |
|-------|-------------|----------|
| A | Cluster-Wide Health & Platform | 14 |
| B | Node Health (Master & Worker) | 7 |
| C | Control Plane | 6 |
| D | Certificates | 5 |
| E | Projects/Namespaces & Quotas | 4 |
| F | Application Health | 8 |
| G | Storage (PV/PVC/SC) | 4 |
| H | Networking | 3 |
| I | Logging & Events | 5 |
| J | Performance & Resource Metrics | 3 |
| K | Service Mesh (Istio & Kiali) | 5 |
| L | Data Grid (Infinispan/Hazelcast) | 4 |
| M | 3Scale API Management | 3 |
| N | Kafka (Strimzi/Red Hat) | 4 |
| O | Storage Platform (ODF/Ceph) | 3 |
| P | MQ / Streaming | 2 |
| Q | HashiCorp Vault | 2 |
| R | Observability Stack | 5 |
| S | Discovery Loops | 2 |
| T | RHACS / ACS Presence | 3 |

## API Endpoints

The application provides the following REST API endpoints:

### GET /api/categories
Returns list of all monitoring categories.

**Response:**
```json
{
  "success": true,
  "categories": [
    {
      "id": "A",
      "name": "Cluster-Wide Health & Platform",
      "commandCount": 14
    }
  ]
}
```

### POST /api/run-monitor
Executes the monitoring script with selected groups.

**Request:**
```json
{
  "groups": ["A", "B", "C"],
  "mode": "actionable"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Monitoring script executed successfully",
  "reportFile": "daily_20250119_143022.html",
  "reportUrl": "/reports/daily_20250119_143022.html"
}
```

### GET /api/reports
Returns list of generated reports.

**Response:**
```json
{
  "success": true,
  "reports": [
    {
      "name": "daily_20250119_143022.html",
      "size": 152340,
      "created": "2025-01-19T14:30:22.000Z",
      "url": "/reports/daily_20250119_143022.html"
    }
  ]
}
```

## Configuration

### Script Location
The servlet automatically detects the script directory. It assumes the following structure:
```
monitoring_commands_v8/
├── openshift_intelligent_monitor_v8.sh
├── monitoring_commands_v8.list
├── reports/
└── webapp/
```

### Servlet Configuration
Edit `MonitorServlet.java:init()` if you need to customize the script directory path.

### Timeouts
Default timeout for script execution is 10 minutes. Modify in `MonitorServlet.java`:
```java
timeout: 600000 // milliseconds
```

## Troubleshooting

### Port Already in Use
If port 8080 is already in use, specify a different port:
```bash
mvn jetty:run -Djetty.http.port=9090
```

### Script Not Found
Ensure the monitoring script is in the correct location relative to the webapp:
```
monitoring_commands_v8/openshift_intelligent_monitor_v8.sh
```

### Permission Denied
Ensure the script has execute permissions:
```bash
chmod +x ../openshift_intelligent_monitor_v8.sh
```

### No Reports Generated
Check that:
1. The `reports/` directory exists in the parent directory
2. The monitoring script has write permissions
3. You're logged into OpenShift (`oc whoami`)

### Java Version Issues
Ensure Java 11+ is installed:
```bash
java -version
```

## Development

### Building
```bash
mvn clean package
```

### Running Tests
```bash
mvn test
```

### Hot Reload
When using Jetty, the application will automatically reload when files change:
```bash
mvn jetty:run
```

## Security Notes

- The CORS filter is enabled for development. Remove or restrict it for production use.
- Ensure proper authentication is in place if deploying to production
- The application executes shell commands - ensure proper access controls

## License

This application is part of the OpenShift Intelligent Monitor project.

## Support

For issues or questions, please refer to the main monitoring script documentation.
