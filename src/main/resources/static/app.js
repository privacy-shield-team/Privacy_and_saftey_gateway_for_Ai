// State
let currentTab = 'identify';
let debounceTimer = null;
let currentActivePolicy = 'DEFAULT';
let allAuditLogs = [];

// Tab Switching
function switchTab(tabId) {
    currentTab = tabId;
    ['identify', 'mask', 'unmask'].forEach(id => {
        const el = document.getElementById(id);
        const btn = document.getElementById('tab-btn-' + id);
        if (el) el.style.display = (id === tabId) ? 'flex' : 'none';
        if (btn) {
            btn.classList.toggle('active', id === tabId);
            btn.setAttribute('aria-selected', id === tabId);
        }
    });

    const activeBox = document.getElementById(tabId + '-user-input-box');
    if (activeBox) {
        activeBox.focus();
        triggerProcessing(activeBox.value);
    }
}

// Real-time Event Listeners
document.getElementById('identify-user-input-box').addEventListener('input', (e) => debounceInput(e.target.value));
document.getElementById('mask-user-input-box').addEventListener('input', (e) => debounceInput(e.target.value));
document.getElementById('unmask-user-input-box').addEventListener('input', (e) => debounceInput(e.target.value));

function debounceInput(text) {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => triggerProcessing(text), 250);
}

// Sample Data Clicks
document.querySelectorAll('.sample-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        const text = btn.getAttribute('data-content');
        const box = document.getElementById(currentTab + '-user-input-box');
        if (box) {
            box.value = text;
            box.focus();
            triggerProcessing(text);
        }
    });
});

function clearAllInputs() {
    ['identify', 'mask', 'unmask'].forEach(id => {
        const box = document.getElementById(id + '-user-input-box');
        if (box) box.value = '';
    });
    document.getElementById('identify-system-output-box').innerHTML = 'Sensitive data will be highlighted here in real-time...';
    document.getElementById('identify-system-output-box').classList.add('is-placeholder');
    document.getElementById('identified-data-list').innerHTML = '<tr class="placeholder"><td colspan="2">Identified sensitive data will appear here...</td></tr>';
    
    document.getElementById('mask-system-output-box').textContent = 'Masked data will appear here...';
    document.getElementById('mask-system-output-box').classList.add('is-placeholder');
    document.getElementById('masked-data-list').innerHTML = '<tr class="placeholder"><td colspan="2">Masked data pairs will appear here...</td></tr>';
    document.getElementById('toxicity-score-2').textContent = 'Score will appear here...';

    document.getElementById('unmask-system-output-box').textContent = 'Unmasked data will appear here...';
    document.getElementById('unmask-system-output-box').classList.add('is-placeholder');
    document.getElementById('toxicity-score-3').textContent = 'Score will appear here...';
}

async function triggerProcessing(text) {
    if (!text || !text.trim()) {
        clearAllInputs();
        return;
    }

    if (currentTab === 'identify') {
        await processIdentify(text);
    } else if (currentTab === 'mask') {
        await processMask(text);
    } else if (currentTab === 'unmask') {
        await processUnmask(text);
    }
}

async function processIdentify(text) {
    const sysOut = document.getElementById('identify-system-output-box');
    const tableBody = document.getElementById('identified-data-list');

    try {
        const res = await fetch('/api/demo/identify', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ text })
        });
        const json = await res.json();
        if (json.success && json.data) {
            sysOut.classList.remove('is-placeholder');
            sysOut.innerHTML = json.data.highlighted_text || 'No sensitive data detected.';

            const entities = json.data.identified_entities || [];
            if (!entities.length) {
                tableBody.innerHTML = '<tr><td colspan="2" style="text-align:center; color:#10b981;">✓ No PII entities found</td></tr>';
            } else {
                tableBody.innerHTML = entities.map(e => `
                    <tr>
                        <td><span class="pii-type-label">${e.pii_type}</span></td>
                        <td><strong>${escapeHtml(e.value)}</strong></td>
                    </tr>
                `).join('');
            }
        }
    } catch (err) {
        sysOut.textContent = 'Error: ' + err.message;
    }
}

async function processMask(text) {
    const sysOut = document.getElementById('mask-system-output-box');
    const tableBody = document.getElementById('masked-data-list');
    const toxBox = document.getElementById('toxicity-score-2');

    try {
        const res = await fetch('/api/demo/mask', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ text })
        });
        const json = await res.json();
        if (json.success && json.data) {
            sysOut.classList.remove('is-placeholder');
            sysOut.textContent = json.data.masked_text || '';

            const pairs = json.data.masked_pairs || [];
            if (!pairs.length) {
                tableBody.innerHTML = '<tr><td colspan="2" style="text-align:center;">No sensitive values to mask</td></tr>';
            } else {
                tableBody.innerHTML = pairs.map(p => `
                    <tr>
                        <td>
                            <strong>${escapeHtml(p.original)}</strong><br>
                            <span class="pii-type-label">${p.type}</span>
                        </td>
                        <td style="color:#0d7c7e; font-family:'JetBrains Mono',monospace;">${escapeHtml(p.masked)}</td>
                    </tr>
                `).join('');
            }

            // Render toxicity chips
            renderToxicity(toxBox, json.data.toxicity_analysis);
        }
    } catch (err) {
        sysOut.textContent = 'Error: ' + err.message;
    }
}

async function processUnmask(text) {
    const sysOut = document.getElementById('unmask-system-output-box');
    const toxBox = document.getElementById('toxicity-score-3');

    try {
        const res = await fetch('/api/demo/unmask', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ text })
        });
        const json = await res.json();
        if (json.success && json.data) {
            sysOut.classList.remove('is-placeholder');
            const data = (json.data.data && json.data.data[0]) ? json.data.data[0] : {};
            sysOut.textContent = data.value || text;

            renderToxicity(toxBox, data.toxicity_analysis);
        }
    } catch (err) {
        sysOut.textContent = 'Error: ' + err.message;
    }
}

function renderToxicity(container, analysis) {
    if (!analysis) return;
    container.classList.remove('is-placeholder');
    let html = '';
    for (const [key, val] of Object.entries(analysis)) {
        const pct = Math.round(val * 100);
        const isHigh = val >= 0.5;
        html += `
            <span class="toxicity-chip ${isHigh ? 'is-high' : ''}">
                <span>${key.replace('_', ' ')}:</span>
                <strong>${pct}%</strong>
            </span>
        `;
    }
    container.innerHTML = html;
}

// Policy Management: Toggle & Sync
async function toggleActivePolicy(policyName) {
    try {
        const res = await fetch('/api/policies/active', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ policy: policyName })
        });
        if (res.ok) {
            const data = await res.json();
            currentActivePolicy = data.activePolicy || policyName;
            updatePolicyUiState(currentActivePolicy);
            loadPolicies();
        }
    } catch (e) {
        console.error('Failed to toggle policy:', e);
    }
}

function updatePolicyUiState(activePolicy) {
    ['DEFAULT', 'FINANCIAL_PII', 'STRICT_COMPLIANCE'].forEach(pol => {
        const btn = document.getElementById('nav-pol-' + pol);
        if (btn) {
            btn.classList.toggle('active', pol === activePolicy);
        }
    });

    const chatPol = document.getElementById('chatPolicySelect');
    if (chatPol) chatPol.value = activePolicy;

    const docPol = document.getElementById('doc-policy-select');
    if (docPol) docPol.value = activePolicy;
}

function syncPolicySelect(val) {
    toggleActivePolicy(val);
}

async function loadPolicies() {
    try {
        const res = await fetch('/api/policies');
        if (res.ok) {
            const policies = await res.json();
            const container = document.getElementById('policy-cards-container');
            if (!container) return;

            let html = '';
            for (const [name, pol] of Object.entries(policies)) {
                const isActive = name.toUpperCase() === currentActivePolicy.toUpperCase();
                html += `
                    <div class="policy-card ${isActive ? 'is-active' : ''}">
                        <div>
                            <div class="policy-card-header">
                                <span class="policy-card-title">${escapeHtml(name)}</span>
                                ${isActive ? '<span class="policy-card-badge">ACTIVE</span>' : ''}
                            </div>
                            <p class="policy-card-desc">${getPolicyDescription(name)}</p>
                            <ul class="policy-features-list">
                                <li><strong>Strategy:</strong> ${pol.strategy}</li>
                                <li><strong>Block on Secrets:</strong> ${pol.blockOnCredentials ? 'YES' : 'NO'}</li>
                                <li><strong>Block on Injection:</strong> ${pol.blockOnInjection ? 'YES' : 'NO'}</li>
                                <li><strong>Strict PII Block:</strong> ${pol.strictMode ? 'YES' : 'NO'}</li>
                            </ul>
                        </div>
                        <button class="policy-activate-btn" onclick="toggleActivePolicy('${escapeHtml(name)}')">
                            ${isActive ? '✓ Currently Active' : 'Activate Policy'}
                        </button>
                    </div>
                `;
            }
            container.innerHTML = html;
        }
    } catch (e) {
        console.error('Error loading policies:', e);
    }
}

function getPolicyDescription(name) {
    switch (name.toUpperCase()) {
        case 'DEFAULT':
            return 'Balanced enterprise privacy protection. Redacts all PII and blocks raw passwords/injections.';
        case 'FINANCIAL_PII':
            return 'Financial services policy. Preserves format with partial masking for cards, PAN, and accounts.';
        case 'STRICT_COMPLIANCE':
            return 'Zero-tolerance compliance mode. Intercepts and blocks prompt if ANY sensitive data is detected.';
        case 'SMART_TOKENIZE':
            return 'High-fidelity reversible tokenization. AI model operates on tokens, restored at client edge.';
        case 'DEVELOPER_PERMISSIVE':
            return 'Permissive logging preset for engineering sandbox and testing without blocking.';
        default:
            return 'Configured privacy and security policy profile.';
    }
}

// End-to-End Chat & Gateway Proxy Call
async function submitToGateway() {
    const currentInputBox = document.getElementById(currentTab + '-user-input-box');
    const rawPrompt = (currentInputBox && currentInputBox.value.trim()) ? currentInputBox.value.trim() : '';

    if (!rawPrompt) {
        alert('Please enter or select a prompt first in the active sandbox tab.');
        return;
    }

    const policy = document.getElementById('chatPolicySelect').value;
    const strategy = document.getElementById('chatStrategySelect').value;
    const provider = document.getElementById('chatProviderSelect').value;
    const isOverride = document.getElementById('gw-override-chk').checked;

    const protView = document.getElementById('gw-protected-prompt');
    const modelView = document.getElementById('gw-model-output');
    const alertBox = document.getElementById('gw-override-alert');

    protView.textContent = 'Intercepting, sanitizing, and evaluating prompt...';
    modelView.textContent = 'Awaiting AI model response...';

    try {
        const res = await fetch('/api/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                prompt: rawPrompt,
                policy: policy,
                overrideStrategy: strategy,
                provider: provider,
                userConfirmedOverride: isOverride
            })
        });

        const data = await res.json();

        if (data.blocked) {
            alertBox.style.display = 'flex';
            protView.textContent = '[GATEWAY REJECTED - NOT FORWARDED TO AI SERVICE]\nReason: ' + data.blockReason;
            modelView.textContent = '[AI SERVICE WAS NOT INVOKED DUE TO SECURITY POLICY BLOCK]';
        } else {
            alertBox.style.display = 'none';
            document.getElementById('gw-override-chk').checked = false;
            protView.textContent = data.protectedPrompt || data.originalPrompt;
            modelView.textContent = data.modelAnswer || 'No answer returned.';
        }

        refreshAuditLogs();
    } catch (e) {
        protView.textContent = 'Error: ' + e.message;
    }
}

// Document & Raw Text Scanner (/api/scan-document)
function loadSampleDocText() {
    const sample = `CONFIDENTIAL CLIENT FINANCIAL SUMMARY\n` +
        `Client Name: Johnathan Doe\n` +
        `Email: j.doe@megacorp.com | Direct: +1 (555) 345-6789\n` +
        `SSN: 123-45-6789 | PAN: ABCDE1234F\n` +
        `Billing Card: 4012-8888-8888-1881 (Exp: 11/29, CVV: 456)\n` +
        `Internal Database Secret: SuperSecretDbPassword#2026\n` +
        `Project Codename: Project Titan\n` +
        `Notes: Please generate an executive financial assessment for this portfolio without leaking raw credentials.`;

    document.getElementById('doc-raw-text').value = sample;
    document.getElementById('doc-name').value = 'Customer_Portfolio_Summary.pdf';
}

function handleDocumentFileUpload(event) {
    const file = event.target.files[0];
    if (!file) return;

    document.getElementById('doc-name').value = file.name;
    const reader = new FileReader();
    reader.onload = (e) => {
        document.getElementById('doc-raw-text').value = e.target.result;
    };
    reader.readAsText(file);
}

async function runDocumentScan() {
    const content = document.getElementById('doc-raw-text').value.trim();
    const docName = document.getElementById('doc-name').value.trim() || 'Document.txt';
    const policy = document.getElementById('doc-policy-select').value;

    if (!content) {
        alert('Please enter or load document text to scan.');
        return;
    }

    const preview = document.getElementById('doc-sanitized-preview');
    const findingsList = document.getElementById('doc-findings-list');

    preview.textContent = 'Scanning and sanitizing document content...';

    try {
        const res = await fetch('/api/scan-document', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                documentName: docName,
                content: content,
                policy: policy
            })
        });

        const data = await res.json();

        // Update chips
        document.getElementById('doc-stat-status').textContent = data.actionTaken;
        document.getElementById('doc-stat-risk').innerHTML = `<span class="risk-chip risk-${data.riskLevel}">${data.riskLevel}</span>`;
        document.getElementById('doc-stat-findings').textContent = data.findingsCount || 0;
        document.getElementById('doc-stat-chars').textContent = data.characterCount || 0;

        // Update preview
        preview.textContent = data.sanitizedContent || 'Clean document.';

        // Update findings list
        const detections = data.detections || [];
        if (!detections.length) {
            findingsList.innerHTML = '<tr><td colspan="3" style="text-align:center; color:#10b981;">✓ No sensitive PII found. Safe document.</td></tr>';
        } else {
            findingsList.innerHTML = detections.map(d => `
                <tr>
                    <td><span class="pii-type-label">${escapeHtml(d.category)}</span></td>
                    <td><strong>${escapeHtml(d.matchedText)}</strong></td>
                    <td style="color:#0d7c7e;">${escapeHtml(d.sanitizedText)}</td>
                </tr>
            `).join('');
        }

        refreshAuditLogs();
    } catch (e) {
        preview.textContent = 'Scan error: ' + e.message;
    }
}

// Copy Helper
function copyBox(id, btn) {
    const el = document.getElementById(id);
    if (!el) return;
    const text = el.textContent.trim();
    navigator.clipboard.writeText(text);
    const orig = btn.textContent;
    btn.textContent = '✓ Copied!';
    setTimeout(() => btn.textContent = orig, 1500);
}

// Audit Logs & Stats
async function refreshAuditLogs() {
    try {
        const res = await fetch('/api/audit');
        if (res.ok) {
            allAuditLogs = await res.json();
            renderFilteredAuditLogs();
        }

        const statRes = await fetch('/api/stats');
        if (statRes.ok) {
            const stats = await statRes.json();
            document.getElementById('stat-total').innerText = stats.totalRequests || 0;
            document.getElementById('stat-blocked').innerText = stats.blockedRequests || 0;
            document.getElementById('stat-sanitized').innerText = stats.sanitizedRequests || 0;
        }

        // Fetch health & model status
        const healthRes = await fetch('/api/health');
        if (healthRes.ok) {
            const h = await healthRes.json();
            if (h.activeModel) {
                document.getElementById('gw-provider').textContent = 'MODEL: ' + h.activeModel.toUpperCase();
            }
            if (h.activePolicy) {
                currentActivePolicy = h.activePolicy;
                updatePolicyUiState(currentActivePolicy);
            }
        }
    } catch (e) {
        console.error(e);
    }
}

function filterAuditLogs() {
    renderFilteredAuditLogs();
}

function renderFilteredAuditLogs() {
    const query = (document.getElementById('audit-search').value || '').toLowerCase().trim();
    const actionFilter = document.getElementById('audit-action-filter').value;
    const tbody = document.getElementById('audit-table-body');

    let filtered = allAuditLogs.filter(item => {
        if (actionFilter !== 'ALL' && item.action !== actionFilter) return false;
        if (query) {
            const str = (item.policyName + ' ' + item.action + ' ' + item.riskLevel + ' ' + item.detectedCategories + ' ' + item.modelUsed).toLowerCase();
            if (!str.includes(query)) return false;
        }
        return true;
    });

    if (!filtered.length) {
        tbody.innerHTML = '<tr><td colspan="8" style="text-align:center; padding:18px;">No audit records match the current filter.</td></tr>';
        return;
    }

    tbody.innerHTML = filtered.map(l => {
        const riskClass = 'risk-' + (l.riskLevel || 'LOW');
        return `
            <tr>
                <td>${escapeHtml(l.timestamp ? l.timestamp.replace('T', ' ').substring(0, 19) : '')}</td>
                <td><span class="pii-type-label">${escapeHtml(l.policyName || '')}</span></td>
                <td><strong>${escapeHtml(l.action || '')}</strong></td>
                <td><span class="risk-chip ${riskClass}">${escapeHtml(l.riskLevel || 'LOW')}</span></td>
                <td>${escapeHtml(l.detectedCategories || '-')}</td>
                <td>${escapeHtml(l.injectionFlags || '-')}</td>
                <td>${escapeHtml(l.modelUsed || '-')}</td>
                <td>${l.latencyMs || 0}ms</td>
            </tr>
        `;
    }).join('');
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

// Initial Boot
refreshAuditLogs();
loadPolicies();
