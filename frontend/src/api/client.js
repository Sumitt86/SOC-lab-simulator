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
