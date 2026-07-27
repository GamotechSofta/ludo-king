import { clearToken, getToken } from "./authStorage";

const AUTH_BASE =
  import.meta.env.VITE_AUTH_API_BASE_URL || "/api/v1/admin/auth";
const PL_BASE =
  import.meta.env.VITE_API_BASE_URL || "/api/v1/admin/profit-loss";
const SETTINGS_BASE =
  import.meta.env.VITE_SETTINGS_API_BASE_URL || "/api/v1/admin/settings";

let onUnauthorized = null;

/** Register a callback invoked on any 401 (clears session in App). */
export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler;
}

function buildQuery(params = {}) {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") return;
    q.set(key, String(value));
  });
  const s = q.toString();
  return s ? `?${s}` : "";
}

async function request(url, options = {}) {
  const headers = {
    Accept: "application/json",
    ...(options.body ? { "Content-Type": "application/json" } : {}),
    ...options.headers,
  };

  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const res = await fetch(url, { ...options, headers });

  if (res.status === 401) {
    clearToken();
    if (onUnauthorized) onUnauthorized();
    const err = new Error("Unauthorized");
    err.status = 401;
    throw err;
  }

  const text = await res.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }

  if (!res.ok) {
    const message =
      (data && (data.message || data.error || data.errorMessage)) ||
      `Request failed (${res.status})`;
    const err = new Error(message);
    err.status = res.status;
    err.data = data;
    throw err;
  }

  return data;
}

/* ---------- Auth ---------- */

export function login(email, password) {
  return request(`${AUTH_BASE}/login`, {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export function logout() {
  return request(`${AUTH_BASE}/logout`, { method: "POST" });
}

export function fetchMe() {
  return request(`${AUTH_BASE}/me`);
}

/* ---------- Profit & Loss ---------- */

export function fetchSummary(filters = {}) {
  return request(`${PL_BASE}/summary${buildQuery(filters)}`);
}

export function fetchGames({ page = 1, limit = 20, ...filters } = {}) {
  return request(
    `${PL_BASE}/games${buildQuery({ page, limit, ...filters })}`
  );
}

export function fetchUsers({ page = 1, limit = 20, ...filters } = {}) {
  return request(
    `${PL_BASE}/users${buildQuery({ page, limit, ...filters })}`
  );
}

/* ---------- Settings ---------- */

export function fetchSettings() {
  return request(`${SETTINGS_BASE}`);
}

export function updateSettings(body) {
  return request(`${SETTINGS_BASE}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}
