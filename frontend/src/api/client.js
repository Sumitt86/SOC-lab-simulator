const API_BASE = import.meta.env.VITE_API_BASE_URL || "/api";

async function handleResponse(response) {
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed with ${response.status}`);
  }
  return response.json();
}

export async function getSummary() {
  return handleResponse(await fetch(`${API_BASE}/dashboard/summary`));
}

export async function getLogs() {
  return handleResponse(await fetch(`${API_BASE}/dashboard/logs`));
}

export async function getAlerts() {
  return handleResponse(await fetch(`${API_BASE}/dashboard/alerts`));
}

export async function getTodos() {
  return handleResponse(await fetch(`${API_BASE}/todos`));
}

export async function toggleTodo(id) {
  return handleResponse(
    await fetch(`${API_BASE}/todos/${id}/toggle`, {
      method: "PATCH"
    })
  );
}

export async function resetSimulation() {
  return handleResponse(
    await fetch(`${API_BASE}/simulation/reset`, {
      method: "POST"
    })
  );
}

export async function startGame(difficulty = "MEDIUM", persistSignatures = false) {
  return handleResponse(
    await fetch(`${API_BASE}/simulation/start`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ difficulty, persistSignatures })
    })
  );
}

export async function resolveAlert(id) {
  return handleResponse(
    await fetch(`${API_BASE}/alerts/${id}/resolve`, {
      method: "POST"
    })
  );
}

export async function blockIP(ip) {
  return handleResponse(
    await fetch(`${API_BASE}/actions/block-ip`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ip })
    })
  );
}

export async function isolateHost(hostId) {
  return handleResponse(
    await fetch(`${API_BASE}/actions/isolate-host`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ hostId })
    })
  );
}

export async function killProcess(pid) {
  return handleResponse(
    await fetch(`${API_BASE}/actions/kill-process`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ pid })
    })
  );
}

export async function removeCron() {
  return handleResponse(
    await fetch(`${API_BASE}/actions/remove-cron`, {
      method: "POST"
    })
  );
}

export async function getAvailableActions() {
  return handleResponse(await fetch(`${API_BASE}/actions/available`));
}

// ==================== RED TEAM ====================

export async function redTeamExecute(container, command) {
  return handleResponse(
    await fetch(`${API_BASE}/red-team/execute`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ container, command })
    })
  );
}

export async function redTeamUploadMalware(name, payload, obfuscation = "none", targetPath = null) {
  const body = { name, payload, obfuscation };
  if (targetPath) body.targetPath = targetPath;
  return handleResponse(
    await fetch(`${API_BASE}/red-team/upload-malware`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    })
  );
}

export async function redTeamHistory() {
  return handleResponse(await fetch(`${API_BASE}/red-team/history`));
}

// ==================== BLUE TEAM ====================

export async function blueTeamProcesses() {
  return handleResponse(await fetch(`${API_BASE}/blue-team/processes`));
}

export async function blueTeamConnections() {
  return handleResponse(await fetch(`${API_BASE}/blue-team/connections`));
}

export async function blueTeamFiles() {
  return handleResponse(await fetch(`${API_BASE}/blue-team/files`));
}

export async function blueTeamScanFile(filePath) {
  return handleResponse(
    await fetch(`${API_BASE}/blue-team/scan-file`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ filePath })
    })
  );
}

export async function blueTeamBehavioralAnalysis(minutes = 5) {
  return handleResponse(await fetch(`${API_BASE}/blue-team/behavioral-analysis?minutes=${minutes}`));
}

// ==================== NEW: Malware Analysis ====================

export async function analyzeFile(filePath) {
  return handleResponse(
    await fetch(`${API_BASE}/blue-team/analyze-file`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ filePath })
    })
  );
}

// ==================== NEW: Signatures ====================

export async function createSignature(type, pattern, description = "") {
  return handleResponse(
    await fetch(`${API_BASE}/blue-team/signatures`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ type, pattern, description })
    })
  );
}

export async function getSignatures() {
  return handleResponse(await fetch(`${API_BASE}/blue-team/signatures`));
}

export async function deleteSignature(id) {
  return handleResponse(
    await fetch(`${API_BASE}/blue-team/signatures/${id}`, { method: "DELETE" })
  );
}

// ==================== NEW: Honeypot ====================

export async function plantHoneypot(path) {
  return handleResponse(
    await fetch(`${API_BASE}/blue-team/plant-honeypot`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ path })
    })
  );
}

export async function plantHttpHoneypot(endpoint) {
  return handleResponse(
    await fetch(`${API_BASE}/blue-team/plant-http-honeypot`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ endpoint: endpoint || "/api/c2/execute" })
    })
  );
}

// ==================== NEW: Analyst Notes ====================

export async function updateAlertNotes(alertId, notes) {
  return handleResponse(
    await fetch(`${API_BASE}/blue-team/alerts/${alertId}/notes`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ notes })
    })
  );
}

// ==================== NEW: Red Team Streaming ====================

export async function redTeamExecuteStream(container, command) {
  return handleResponse(
    await fetch(`${API_BASE}/red-team/execute-stream`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ container, command })
    })
  );
}

// ==================== Network Monitoring ====================

export async function startNetworkMonitoring() {
  return handleResponse(
    await fetch(`${API_BASE}/blue-team/network/start`, { method: "POST" })
  );
}

export async function stopNetworkMonitoring() {
  return handleResponse(
    await fetch(`${API_BASE}/blue-team/network/stop`, { method: "POST" })
  );
}

export async function getNetworkEvents(limit = 100) {
  return handleResponse(await fetch(`${API_BASE}/blue-team/network/events?limit=${limit}`));
}

export async function getNetworkStats() {
  return handleResponse(await fetch(`${API_BASE}/blue-team/network/stats`));
}

export async function networkBlockIp(ip) {
  return handleResponse(
    await fetch(`${API_BASE}/blue-team/network/block`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ip })
    })
  );
}

export async function networkRejectIp(ip) {
  return handleResponse(
    await fetch(`${API_BASE}/blue-team/network/reject`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ip })
    })
  );
}

export async function networkTerminateConnections(ip) {
  return handleResponse(
    await fetch(`${API_BASE}/blue-team/network/terminate`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ip })
    })
  );
}

export async function activeNetworkProbe() {
  return handleResponse(
    await fetch(`${API_BASE}/blue-team/network/probe`, { method: "POST" })
  );
}

// ==================== Mock AV Engine Scan ====================

export async function avScanHash(hash) {
  return handleResponse(
    await fetch(`${API_BASE}/blue-team/av-scan`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ hash })
    })
  );
}

export async function avScanDeploy(hash) {
  return handleResponse(
    await fetch(`${API_BASE}/blue-team/av-scan/deploy`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ hash })
    })
  );
}

// ==================== NEW: Game Report ====================

export async function getGameReport() {
  return handleResponse(await fetch(`${API_BASE}/game/report`));
}
