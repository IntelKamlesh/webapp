/**
 * OpenShift Monitor Web Application
 * Main application logic for monitoring groups and report generation
 *
 * @author OpenShift Monitor Team
 * @version 2.0
 */

// ==================== Application State ====================

const AppState = {
    categories: [],
    selectedGroups: new Set(),
    currentTab: null,
    isMonitorRunning: false,
    apiEndpoints: {
        categories: '/api/categories',
        reports: '/api/reports',
        runMonitor: '/api/run-monitor'
    }
};

// ==================== DOM References ====================

const DOM = {
    selectAllBtn: null,
    deselectAllBtn: null,
    runBtn: null,
    btnText: null,
    spinner: null,
    tabs: null,
    tabContent: null,
    statusMessage: null,
    reportsList: null
};

// ==================== Initialization ====================

/**
 * Initialize application on DOM ready
 */
document.addEventListener('DOMContentLoaded', () => {
    initializeDOMReferences();
    setupEventListeners();
    loadInitialData();
});

/**
 * Cache DOM element references
 */
function initializeDOMReferences() {
    DOM.selectAllBtn = document.getElementById('selectAllBtn');
    DOM.deselectAllBtn = document.getElementById('deselectAllBtn');
    DOM.runBtn = document.getElementById('runBtn');
    DOM.btnText = DOM.runBtn ? DOM.runBtn.querySelector('.btn-text') : null;
    DOM.spinner = DOM.runBtn ? DOM.runBtn.querySelector('.spinner') : null;
    DOM.tabs = document.getElementById('tabs');
    DOM.tabContent = document.getElementById('tabContent');
    DOM.statusMessage = document.getElementById('statusMessage');
    DOM.reportsList = document.getElementById('reportsList');
}

/**
 * Setup all event listeners
 */
function setupEventListeners() {
    if (DOM.selectAllBtn) {
        DOM.selectAllBtn.addEventListener('click', handleSelectAll);
    }
    if (DOM.deselectAllBtn) {
        DOM.deselectAllBtn.addEventListener('click', handleDeselectAll);
    }
    if (DOM.runBtn) {
        DOM.runBtn.addEventListener('click', handleRunMonitor);
    }
}

/**
 * Load initial data
 */
async function loadInitialData() {
    try {
        await Promise.all([
            loadCategories(),
            loadGlobalReports()
        ]);
    } catch (error) {
        console.error('Failed to load initial data:', error);
        showMessage('Failed to load application data. Please refresh the page.', 'error');
    }
}

// ==================== API Calls ====================

/**
 * Generic API call wrapper with error handling
 * @param {string} url - API endpoint
 * @param {Object} options - Fetch options
 * @returns {Promise<Object>} API response data
 */
async function apiCall(url, options = {}) {
    try {
        const response = await fetch(url, {
            ...options,
            headers: {
                'Content-Type': 'application/json',
                ...options.headers
            }
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const data = await response.json();

        if (!data.success && data.error) {
            throw new Error(data.error);
        }

        return data;
    } catch (error) {
        console.error('API call failed:', url, error);
        throw error;
    }
}

/**
 * Load monitoring categories
 */
async function loadCategories() {
    try {
        const data = await apiCall(AppState.apiEndpoints.categories);
        AppState.categories = data.categories || data.data || [];

        if (AppState.categories.length > 0) {
            renderTabs();
            if (AppState.categories[0]) {
                showTab(AppState.categories[0].id);
            }
        } else {
            throw new Error('No categories found');
        }
    } catch (error) {
        console.error('Failed to load categories:', error);
        showMessage('Failed to load monitoring categories: ' + error.message, 'error');
        DOM.tabs.innerHTML = '<p class="error-message">Failed to load categories. Please refresh the page.</p>';
    }
}

/**
 * Load global reports list
 */
async function loadGlobalReports() {
    if (!DOM.reportsList) return;

    try {
        const data = await apiCall(AppState.apiEndpoints.reports);
        const reports = data.reports || data.data || [];

        if (reports.length === 0) {
            DOM.reportsList.innerHTML = '<p class="loading">No reports available yet. Run the monitor to generate a report.</p>';
        } else {
            DOM.reportsList.innerHTML = '';
            reports.forEach(report => {
                const reportElement = createReportElement(report);
                DOM.reportsList.appendChild(reportElement);
            });
        }
    } catch (error) {
        console.error('Failed to load reports:', error);
        DOM.reportsList.innerHTML = '<p class="error-message">Failed to load reports</p>';
    }
}

/**
 * Load reports for each tab
 */
async function loadTabReports() {
    setRunButtonState(false);

    try {
        const data = await apiCall(AppState.apiEndpoints.reports);
        const reports = data.reports || data.data || [];

        AppState.categories.forEach(category => {
            const tabReportsContainer = document.querySelector(`#reports-${category.id} .tab-reports-list`);
            if (!tabReportsContainer) return;

            if (reports.length === 0) {
                tabReportsContainer.innerHTML = '<p class="no-reports">No reports generated yet. Select groups and click Run Monitor.</p>';
            } else {
                renderTabReports(tabReportsContainer, reports.slice(0, 5), reports.length);
            }
        });
    } catch (error) {
        console.error('Error loading tab reports:', error);
        AppState.categories.forEach(category => {
            const tabReportsContainer = document.querySelector(`#reports-${category.id} .tab-reports-list`);
            if (tabReportsContainer) {
                tabReportsContainer.innerHTML = '<p class="error-reports">Error loading reports</p>';
            }
        });
    } finally {
        if (!AppState.isMonitorRunning) {
            setRunButtonState(true);
        }
    }
}

// ==================== Rendering Functions ====================

/**
 * Render all category tabs
 */
function renderTabs() {
    if (!DOM.tabs || !DOM.tabContent) return;

    DOM.tabs.innerHTML = '';
    DOM.tabContent.innerHTML = '';

    AppState.categories.forEach(category => {
        // Create tab button
        const tab = createTabButton(category);
        DOM.tabs.appendChild(tab);

        // Create tab content
        const content = createTabContent(category);
        DOM.tabContent.appendChild(content);
    });

    // Load tab reports after rendering
    setTimeout(() => loadTabReports(), 100);
}

/**
 * Create tab button element
 * @param {Object} category - Category data
 * @returns {HTMLElement} Tab button element
 */
function createTabButton(category) {
    const tab = document.createElement('button');
    tab.className = 'tab';
    tab.dataset.category = category.id;
    tab.setAttribute('role', 'tab');
    tab.setAttribute('aria-selected', 'false');
    tab.setAttribute('aria-controls', `panel-${category.id}`);
    tab.id = `tab-${category.id}`;

    tab.innerHTML = `
        <div class="tab-id">${escapeHtml(category.id)}</div>
        <div class="tab-name">${escapeHtml(category.name)}</div>
        <div class="tab-count">${category.commandCount} commands</div>
    `;

    tab.addEventListener('click', () => showTab(category.id));

    return tab;
}

/**
 * Create tab content element
 * @param {Object} category - Category data
 * @returns {HTMLElement} Tab content element
 */
function createTabContent(category) {
    const content = document.createElement('div');
    content.className = 'tab-content';
    content.dataset.category = category.id;
    content.setAttribute('role', 'tabpanel');
    content.setAttribute('id', `panel-${category.id}`);
    content.setAttribute('aria-labelledby', `tab-${category.id}`);

    content.innerHTML = `
        <div class="category-detail">
            <h2>
                <span class="category-id">${escapeHtml(category.id)}</span>
                ${escapeHtml(category.name)}
            </h2>
            <p>This monitoring group contains ${category.commandCount} commands that will check various aspects of your OpenShift cluster.</p>
            <div class="checkbox-container">
                <input type="checkbox" id="check-${category.id}" data-category="${category.id}" aria-label="Include ${category.name} in monitoring run">
                <label for="check-${category.id}">Include this group in monitoring run</label>
            </div>
            <div class="tab-reports" id="reports-${category.id}">
                <h3>Reports for this group</h3>
                <div class="tab-reports-list" role="list">
                    <p class="loading">Loading reports...</p>
                </div>
            </div>
        </div>
    `;

    // Add checkbox listener
    const checkbox = content.querySelector('input[type="checkbox"]');
    if (checkbox) {
        checkbox.addEventListener('change', (e) => handleGroupSelection(e, category.id));
    }

    return content;
}

/**
 * Create report element
 * @param {Object} report - Report data
 * @returns {HTMLElement} Report element
 */
function createReportElement(report) {
    const reportItem = document.createElement('div');
    reportItem.className = 'report-item';
    reportItem.setAttribute('role', 'listitem');

    reportItem.innerHTML = `
        <div class="report-info">
            <div class="report-name">${escapeHtml(report.name)}</div>
            <div class="report-meta">
                Created: ${escapeHtml(formatDate(report.created))} |
                Size: ${formatBytes(report.size)}
            </div>
        </div>
        <div class="report-actions">
            <a href="${escapeHtml(report.url)}" target="_blank" rel="noopener noreferrer" class="report-link" aria-label="View report ${report.name}">View Report</a>
        </div>
    `;

    return reportItem;
}

/**
 * Render reports in tab
 * @param {HTMLElement} container - Container element
 * @param {Array} reports - Reports to display
 * @param {number} totalCount - Total number of reports
 */
function renderTabReports(container, reports, totalCount) {
    container.innerHTML = '';

    reports.forEach(report => {
        const reportItem = document.createElement('div');
        reportItem.className = 'tab-report-item';
        reportItem.setAttribute('role', 'listitem');

        reportItem.innerHTML = `
            <div class="tab-report-info">
                <div class="tab-report-name">${escapeHtml(report.name)}</div>
                <div class="tab-report-meta">
                    ${escapeHtml(formatDate(report.created))} • ${formatBytes(report.size)}
                </div>
            </div>
            <a href="${escapeHtml(report.url)}" target="_blank" rel="noopener noreferrer" class="tab-report-link" aria-label="View report ${report.name}">View</a>
        `;

        container.appendChild(reportItem);
    });

    if (totalCount > 5) {
        const moreInfo = document.createElement('p');
        moreInfo.className = 'more-reports';
        moreInfo.textContent = `+${totalCount - 5} more reports available in "Recent Reports" section below`;
        container.appendChild(moreInfo);
    }
}

// ==================== Event Handlers ====================

/**
 * Handle tab selection
 * @param {string} categoryId - Category ID
 */
function showTab(categoryId) {
    AppState.currentTab = categoryId;

    // Update tab buttons
    document.querySelectorAll('.tab').forEach(tab => {
        const isActive = tab.dataset.category === categoryId;
        tab.setAttribute('aria-selected', isActive.toString());
        if (isActive) {
            tab.style.borderColor = '#667eea';
            tab.style.background = '#f0f0ff';
        } else {
            tab.style.borderColor = '#e9ecef';
            tab.style.background = '#f8f9fa';
        }
    });

    // Update tab content
    document.querySelectorAll('.tab-content').forEach(content => {
        const isActive = content.dataset.category === categoryId;
        if (isActive) {
            content.classList.add('active');
        } else {
            content.classList.remove('active');
        }
    });
}

/**
 * Handle group selection change
 * @param {Event} event - Change event
 * @param {string} categoryId - Category ID
 */
function handleGroupSelection(event, categoryId) {
    const tab = document.querySelector(`.tab[data-category="${categoryId}"]`);

    if (event.target.checked) {
        AppState.selectedGroups.add(categoryId);
        if (tab) tab.classList.add('active');
    } else {
        AppState.selectedGroups.delete(categoryId);
        if (tab) tab.classList.remove('active');
    }
}

/**
 * Handle select all button
 */
function handleSelectAll() {
    AppState.categories.forEach(cat => {
        AppState.selectedGroups.add(cat.id);
        const checkbox = document.getElementById('check-' + cat.id);
        if (checkbox) checkbox.checked = true;
        const tab = document.querySelector(`.tab[data-category="${cat.id}"]`);
        if (tab) tab.classList.add('active');
    });
}

/**
 * Handle deselect all button
 */
function handleDeselectAll() {
    AppState.selectedGroups.clear();
    document.querySelectorAll('input[type="checkbox"]').forEach(cb => {
        cb.checked = false;
    });
    document.querySelectorAll('.tab').forEach(tab => {
        tab.classList.remove('active');
    });
}

/**
 * Handle run monitor button
 */
async function handleRunMonitor() {
    if (AppState.selectedGroups.size === 0) {
        showMessage('Please select at least one monitoring group', 'error');
        return;
    }

    if (AppState.isMonitorRunning) {
        return; // Prevent multiple simultaneous runs
    }

    const mode = document.querySelector('input[name="mode"]:checked')?.value || 'actionable';

    AppState.isMonitorRunning = true;
    setRunButtonState(false, true);
    showMessage('Starting monitoring script... This may take a few minutes.', 'info');

    try {
        const data = await apiCall(AppState.apiEndpoints.runMonitor, {
            method: 'POST',
            body: JSON.stringify({
                groups: Array.from(AppState.selectedGroups),
                mode: mode
            })
        });

        const result = data.data || data;

        if (result.success || data.success) {
            let message = 'Monitoring completed successfully!';
            if (result.reportUrl) {
                message += ` <a href="${escapeHtml(result.reportUrl)}" target="_blank" rel="noopener noreferrer" style="color: #155724; text-decoration: underline;">View Report</a>`;
            }
            showMessage(message, 'success');

            // Refresh reports
            await Promise.all([
                loadGlobalReports(),
                loadTabReports()
            ]);
        } else {
            throw new Error(result.message || 'Unknown error occurred');
        }
    } catch (error) {
        console.error('Monitor execution failed:', error);
        showMessage('Monitoring failed: ' + error.message, 'error');
    } finally {
        AppState.isMonitorRunning = false;
        setRunButtonState(true, false);
    }
}

// ==================== UI Helper Functions ====================

/**
 * Set run button state
 * @param {boolean} enabled - Whether button should be enabled
 * @param {boolean} showSpinner - Whether to show spinner
 */
function setRunButtonState(enabled, showSpinner = false) {
    if (!DOM.runBtn || !DOM.btnText || !DOM.spinner) return;

    DOM.runBtn.disabled = !enabled;
    DOM.btnText.textContent = showSpinner ? 'Running...' : 'Run Monitor';
    DOM.spinner.style.display = showSpinner ? 'inline-block' : 'none';

    DOM.runBtn.setAttribute('aria-busy', showSpinner.toString());
}

/**
 * Show status message
 * @param {string} message - Message text
 * @param {string} type - Message type (success, error, info)
 */
function showMessage(message, type) {
    if (!DOM.statusMessage) return;

    DOM.statusMessage.className = 'status-message ' + type;
    DOM.statusMessage.innerHTML = message;
    DOM.statusMessage.style.display = 'block';

    // Auto-hide success/info messages
    if (type === 'success' || type === 'info') {
        setTimeout(() => {
            DOM.statusMessage.style.display = 'none';
        }, 10000);
    }
}

// ==================== Utility Functions ====================

/**
 * Format bytes to human readable
 * @param {number} bytes - Number of bytes
 * @returns {string} Formatted string
 */
function formatBytes(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}

/**
 * Format date string
 * @param {string} dateString - Date string
 * @returns {string} Formatted date
 */
function formatDate(dateString) {
    try {
        const date = new Date(dateString);
        return date.toLocaleString();
    } catch (error) {
        return dateString;
    }
}

/**
 * Escape HTML to prevent XSS
 * @param {string} unsafe - Unsafe string
 * @returns {string} Safe HTML string
 */
function escapeHtml(unsafe) {
    if (typeof unsafe !== 'string') {
        return unsafe;
    }
    return unsafe
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}
