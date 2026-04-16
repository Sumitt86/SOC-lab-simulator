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

export async function startGame(difficulty = "MEDIUM") {
  return handleResponse(
    await fetch(`${API_BASE}/simulation/start`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ difficulty })
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
