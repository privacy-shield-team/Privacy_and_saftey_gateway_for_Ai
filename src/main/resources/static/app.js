// State
let currentTab = 'identify';
let debounceTimer = null;

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
    document.getElementById('identify-system-output-box').innerHTML = 'Sensitive data will be highlighted here...';
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
    const isOverride = document.getElementById('gw-override-chk').checked;

    const protView = document.getElementById('gw-protected-prompt');
    const modelView = document.getElementById('gw-model-output');
    const alertBox = document.getElementById('gw-override-alert');

    protView.textContent = 'Intercepting and evaluating prompt...';
    modelView.textContent = 'Awaiting model response...';

    try {
        const res = await fetch('/api/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                prompt: rawPrompt,
                policy: policy,
                overrideStrategy: strategy,
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

// Copy Helper
function copyBox(id, btn) {
    const el = document.getElementById(id);
    if (!el) return;
    const text = el.textContent.trim();
    navigator.clipboard.writeText(text);
    btn.textContent = '✓ Copied!';
    setTimeout(() => btn.textContent = '📋 Copy', 1500);
}

// Audit Logs
async function refreshAuditLogs() {
    try {
        const res = await fetch('/api/audit');
        if (res.ok) {
            const logs = await res.json();
            const tbody = document.getElementById('audit-table-body');
            if (!logs.length) {
                tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;">No audit records found.</td></tr>';
                return;
            }
            tbody.innerHTML = logs.map(l => `
                <tr>
                    <td>${l.timestamp.replace('T', ' ').substring(0, 19)}</td>
                    <td><span class="pii-type-label">${l.policyName}</span></td>
                    <td><strong>${l.action}</strong></td>
                    <td style="color:${l.riskLevel === 'CRITICAL' || l.riskLevel === 'HIGH' ? '#ef4444' : '#10b981'}">${l.riskLevel}</td>
                    <td>${l.detectedCategories}</td>
                    <td>${l.injectionFlags}</td>
                    <td>${l.modelUsed}</td>
                    <td>${l.latencyMs}ms</td>
                </tr>
            `).join('');
        }

        const statRes = await fetch('/api/stats');
        if (statRes.ok) {
            const stats = await statRes.json();
            document.getElementById('stat-total').innerText = stats.totalRequests || 0;
            document.getElementById('stat-blocked').innerText = stats.blockedRequests || 0;
            document.getElementById('stat-sanitized').innerText = stats.sanitizedRequests || 0;
        }
    } catch (e) {
        console.error(e);
    }
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

// Initial Run
refreshAuditLogs();
