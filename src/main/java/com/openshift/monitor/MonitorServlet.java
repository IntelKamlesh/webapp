package com.openshift.monitor;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.stream.Collectors;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import com.google.gson.*;

/**
 * Main servlet for OpenShift Monitor Web Application
 * Handles API requests for categories, reports, and monitoring execution
 *
 * @author OpenShift Monitor Team
 * @version 2.0
 */
@WebServlet(name = "MonitorServlet", urlPatterns = {"/api/*"})
public class MonitorServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(MonitorServlet.class.getName());

    // Configuration constants
    private static final String SCRIPT_NAME = "openshift_intelligent_monitor_v8.sh";
    private static final String COMMANDS_FILE_NAME = "monitoring_commands_v8.list";
    private static final String REPORTS_DIR = "reports";
    private static final int SCRIPT_TIMEOUT_MINUTES = 15;
    private static final int MAX_BUFFER_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_REPORTS_TO_RETURN = 50;

    // Instance variables
    private String scriptDir;
    private Gson gson;
    private File commandsFile;
    private File reportsDirectory;

    /**
     * Initialize servlet - locate script directory and validate files
     */
    @Override
    public void init() throws ServletException {
        super.init();

        try {
            // Initialize Gson with pretty printing for development
            gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .create();

            // Get the path to the script directory (parent of webapp)
            String webappPath = getServletContext().getRealPath("/");
            File webapp = new File(webappPath);
            scriptDir = webapp.getParentFile().getParentFile().getParentFile().getAbsolutePath();

            LOGGER.info("Script directory initialized: " + scriptDir);

            // Validate required files
            commandsFile = new File(scriptDir, COMMANDS_FILE_NAME);
            if (!commandsFile.exists() || !commandsFile.canRead()) {
                throw new ServletException("Commands file not found or not readable: " + commandsFile.getAbsolutePath());
            }

            // Validate script exists
            File scriptFile = new File(scriptDir, SCRIPT_NAME);
            if (!scriptFile.exists() || !scriptFile.canExecute()) {
                LOGGER.warning("Script file not found or not executable: " + scriptFile.getAbsolutePath());
            }

            // Ensure reports directory exists
            reportsDirectory = new File(scriptDir, REPORTS_DIR);
            if (!reportsDirectory.exists()) {
                reportsDirectory.mkdirs();
                LOGGER.info("Created reports directory: " + reportsDirectory.getAbsolutePath());
            }

        } catch (Exception e) {
            LOGGER.severe("Failed to initialize servlet: " + e.getMessage());
            throw new ServletException("Servlet initialization failed", e);
        }
    }

    /**
     * Handle GET requests
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String pathInfo = request.getPathInfo();

        if (pathInfo == null || pathInfo.equals("/")) {
            sendErrorResponse(response, "API endpoint not specified", HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        try {
            switch (pathInfo) {
                case "/categories":
                    handleGetCategories(response);
                    break;
                case "/reports":
                    handleGetReports(response);
                    break;
                default:
                    sendErrorResponse(response, "Unknown endpoint: " + pathInfo, HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error handling GET request: " + pathInfo, e);
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Handle POST requests
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String pathInfo = request.getPathInfo();

        if (pathInfo == null || pathInfo.equals("/")) {
            sendErrorResponse(response, "API endpoint not specified", HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        try {
            if ("/run-monitor".equals(pathInfo)) {
                handleRunMonitor(request, response);
            } else {
                sendErrorResponse(response, "Unknown endpoint: " + pathInfo, HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error handling POST request: " + pathInfo, e);
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get all monitoring categories
     */
    private void handleGetCategories(HttpServletResponse response) throws IOException {
        LOGGER.info("Fetching monitoring categories");

        List<Category> categories = getCategories();
        ApiResponse<List<Category>> apiResponse = new ApiResponse<>(true, categories, null);

        sendJsonResponse(response, apiResponse, HttpServletResponse.SC_OK);
    }

    /**
     * Get list of generated reports
     */
    private void handleGetReports(HttpServletResponse response) throws IOException {
        LOGGER.info("Fetching reports list");

        List<ReportFile> reports = getReportsList();
        ApiResponse<List<ReportFile>> apiResponse = new ApiResponse<>(true, reports, null);

        sendJsonResponse(response, apiResponse, HttpServletResponse.SC_OK);
    }

    /**
     * Execute monitoring script with selected groups
     */
    private void handleRunMonitor(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        // Parse request body
        StringBuilder requestBody = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
        }

        MonitorRequest monitorRequest;
        try {
            monitorRequest = gson.fromJson(requestBody.toString(), MonitorRequest.class);
        } catch (JsonSyntaxException e) {
            LOGGER.warning("Invalid JSON in request: " + e.getMessage());
            sendErrorResponse(response, "Invalid JSON format", HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // Validate request
        if (monitorRequest == null || monitorRequest.groups == null || monitorRequest.groups.isEmpty()) {
            sendErrorResponse(response, "No groups selected", HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // Validate group names (prevent injection)
        for (String group : monitorRequest.groups) {
            if (!group.matches("^[A-Z]$")) {
                sendErrorResponse(response, "Invalid group name: " + group, HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
        }

        // Validate mode
        String mode = monitorRequest.mode != null ? monitorRequest.mode : "actionable";
        if (!mode.equals("actionable") && !mode.equals("verbose")) {
            sendErrorResponse(response, "Invalid mode: " + mode, HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        LOGGER.info("Executing monitor for groups: " + String.join(", ", monitorRequest.groups) + " in mode: " + mode);

        // Create temporary commands file
        File tempFile = null;
        try {
            tempFile = createFilteredCommandsFile(monitorRequest.groups);

            // Execute the monitoring script
            MonitorResult result = executeMonitoringScript(tempFile, mode);

            if (result.success) {
                ApiResponse<MonitorResult> apiResponse = new ApiResponse<>(true, result, null);
                sendJsonResponse(response, apiResponse, HttpServletResponse.SC_OK);
            } else {
                sendErrorResponse(response, result.message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to execute monitoring script", e);
            sendErrorResponse(response, "Script execution failed: " + e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            // Clean up temp file
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
                LOGGER.fine("Cleaned up temporary file: " + tempFile.getName());
            }
        }
    }

    /**
     * Create filtered commands file with only selected groups
     */
    private File createFilteredCommandsFile(List<String> groups) throws IOException {
        String tempFileName = "temp_commands_" + System.currentTimeMillis() + ".list";
        File tempFile = new File(scriptDir, tempFileName);

        List<String> lines = Files.readAllLines(commandsFile.toPath());
        List<String> selectedLines = new ArrayList<>();

        // Add header
        selectedLines.add("#!/bin/bash");
        selectedLines.add("# Filtered monitoring commands");
        selectedLines.add("# Generated by OpenShift Monitor Web App");
        selectedLines.add("# Groups: " + String.join(", ", groups));
        selectedLines.add("# Timestamp: " + new Date());
        selectedLines.add("");

        // Filter lines by selected groups
        Set<String> groupSet = new HashSet<>(groups);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                selectedLines.add(line);
            } else if (line.contains("|")) {
                String[] parts = line.split("\\|", 2);
                if (parts.length >= 1) {
                    String category = parts[0].trim();
                    if (groupSet.contains(category)) {
                        selectedLines.add(line);
                    }
                }
            }
        }

        Files.write(tempFile.toPath(), selectedLines);
        LOGGER.info("Created filtered commands file: " + tempFile.getName() + " with " + selectedLines.size() + " lines");

        return tempFile;
    }

    /**
     * Execute the monitoring script
     */
    private MonitorResult executeMonitoringScript(File commandsFile, String mode) throws IOException, InterruptedException {
        File scriptFile = new File(scriptDir, SCRIPT_NAME);

        if (!scriptFile.exists()) {
            return new MonitorResult(false, "Script file not found: " + SCRIPT_NAME, null, null, null);
        }

        String verboseFlag = "verbose".equals(mode) ? "--verbose=true" : "--verbose=false";

        // Build process
        ProcessBuilder pb = new ProcessBuilder("bash", scriptFile.getAbsolutePath(), verboseFlag);
        pb.directory(new File(scriptDir));
        pb.environment().put("COMMANDS_FILE", commandsFile.getAbsolutePath());
        pb.redirectErrorStream(true);

        LOGGER.info("Executing: bash " + scriptFile.getAbsolutePath() + " " + verboseFlag);

        Process process = pb.start();

        // Read output with timeout
        StringBuilder output = new StringBuilder();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<Integer> future = executor.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        if (output.length() > MAX_BUFFER_SIZE) {
                            break; // Prevent memory overflow
                        }
                    }
                }
                return process.waitFor();
            });

            int exitCode = future.get(SCRIPT_TIMEOUT_MINUTES, TimeUnit.MINUTES);

            if (exitCode != 0) {
                LOGGER.warning("Script execution failed with exit code: " + exitCode);
                return new MonitorResult(false, "Script execution failed with exit code: " + exitCode,
                                       null, null, output.toString().substring(0, Math.min(1000, output.length())));
            }

            // Find the latest report
            String latestReport = findLatestReport();
            String reportUrl = latestReport != null ? "/reports/" + latestReport : null;

            LOGGER.info("Script execution completed successfully. Report: " + latestReport);

            return new MonitorResult(true, "Monitoring script executed successfully",
                                   latestReport, reportUrl, output.toString().substring(0, Math.min(1000, output.length())));

        } catch (TimeoutException e) {
            process.destroyForcibly();
            LOGGER.severe("Script execution timed out after " + SCRIPT_TIMEOUT_MINUTES + " minutes");
            return new MonitorResult(false, "Script execution timed out", null, null, null);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Find the most recently created report file
     */
    private String findLatestReport() {
        File[] files = reportsDirectory.listFiles((dir, name) ->
            name.startsWith("daily_") && name.endsWith(".html"));

        if (files == null || files.length == 0) {
            return null;
        }

        File latest = Arrays.stream(files)
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null);

        return latest != null ? latest.getName() : null;
    }

    /**
     * Get list of all report files
     */
    private List<ReportFile> getReportsList() {
        File[] files = reportsDirectory.listFiles((dir, name) ->
            name.startsWith("daily_") && name.endsWith(".html"));

        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        return Arrays.stream(files)
                .sorted((a, b) -> Long.compare(b.lastModified(), a.lastModified()))
                .limit(MAX_REPORTS_TO_RETURN)
                .map(file -> new ReportFile(
                        file.getName(),
                        file.length(),
                        new Date(file.lastModified()).toString(),
                        "/reports/" + file.getName()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Parse categories from commands file
     */
    private List<Category> getCategories() throws IOException {
        Map<String, String> categoryDescriptions = getCategoryDescriptions();
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();

        List<String> lines = Files.readAllLines(commandsFile.toPath());

        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && line.contains("|")) {
                String[] parts = line.split("\\|", 2);
                if (parts.length >= 1) {
                    String category = parts[0].trim();
                    if (category.matches("^[A-Z]$")) {
                        categoryCounts.put(category, categoryCounts.getOrDefault(category, 0) + 1);
                    }
                }
            }
        }

        return categoryCounts.entrySet().stream()
                .map(entry -> new Category(
                        entry.getKey(),
                        categoryDescriptions.getOrDefault(entry.getKey(), "Category " + entry.getKey()),
                        entry.getValue()
                ))
                .sorted(Comparator.comparing(Category::getId))
                .collect(Collectors.toList());
    }

    /**
     * Get category descriptions map
     */
    private Map<String, String> getCategoryDescriptions() {
        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("A", "Cluster-Wide Health & Platform");
        descriptions.put("B", "Node Health (Master & Worker)");
        descriptions.put("C", "Control Plane");
        descriptions.put("D", "Certificates");
        descriptions.put("E", "Projects/Namespaces & Quotas");
        descriptions.put("F", "Application Health");
        descriptions.put("G", "Storage (PV/PVC/SC)");
        descriptions.put("H", "Networking");
        descriptions.put("I", "Logging & Events");
        descriptions.put("J", "Performance & Resource Metrics");
        descriptions.put("K", "Service Mesh (Istio & Kiali)");
        descriptions.put("L", "Data Grid (Infinispan/Hazelcast)");
        descriptions.put("M", "3Scale API Management");
        descriptions.put("N", "Kafka (Strimzi/Red Hat)");
        descriptions.put("O", "Storage Platform (ODF/Ceph)");
        descriptions.put("P", "MQ / Streaming");
        descriptions.put("Q", "HashiCorp Vault");
        descriptions.put("R", "Observability Stack");
        descriptions.put("S", "Discovery Loops");
        descriptions.put("T", "RHACS / ACS Presence");
        return descriptions;
    }

    /**
     * Send JSON response
     */
    private void sendJsonResponse(HttpServletResponse response, Object data, int statusCode) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(statusCode);

        try (PrintWriter out = response.getWriter()) {
            out.write(gson.toJson(data));
        }
    }

    /**
     * Send error response
     */
    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode) throws IOException {
        LOGGER.warning("Sending error response: " + message + " (status: " + statusCode + ")");
        ApiResponse<Object> error = new ApiResponse<>(false, null, message);
        sendJsonResponse(response, error, statusCode);
    }

    // ==================== Inner Classes ====================

    /**
     * Generic API response wrapper
     */
    static class ApiResponse<T> {
        private final boolean success;
        private final T data;
        private final String error;

        public ApiResponse(boolean success, T data, String error) {
            this.success = success;
            this.data = data;
            this.error = error;
        }

        public boolean isSuccess() { return success; }
        public T getData() { return data; }
        public String getError() { return error; }
    }

    /**
     * Category model
     */
    static class Category {
        private final String id;
        private final String name;
        private final int commandCount;

        public Category(String id, String name, int commandCount) {
            this.id = id;
            this.name = name;
            this.commandCount = commandCount;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public int getCommandCount() { return commandCount; }
    }

    /**
     * Report file model
     */
    static class ReportFile {
        private final String name;
        private final long size;
        private final String created;
        private final String url;

        public ReportFile(String name, long size, String created, String url) {
            this.name = name;
            this.size = size;
            this.created = created;
            this.url = url;
        }

        public String getName() { return name; }
        public long getSize() { return size; }
        public String getCreated() { return created; }
        public String getUrl() { return url; }
    }

    /**
     * Monitor request model
     */
    static class MonitorRequest {
        private List<String> groups;
        private String mode;

        public List<String> getGroups() { return groups; }
        public void setGroups(List<String> groups) { this.groups = groups; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
    }

    /**
     * Monitor result model
     */
    static class MonitorResult {
        private final boolean success;
        private final String message;
        private final String reportFile;
        private final String reportUrl;
        private final String output;

        public MonitorResult(boolean success, String message, String reportFile, String reportUrl, String output) {
            this.success = success;
            this.message = message;
            this.reportFile = reportFile;
            this.reportUrl = reportUrl;
            this.output = output;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getReportFile() { return reportFile; }
        public String getReportUrl() { return reportUrl; }
        public String getOutput() { return output; }
    }
}
