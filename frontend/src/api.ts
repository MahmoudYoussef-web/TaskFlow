const BASE = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

export type TaskStatus = "TODO" | "IN_PROGRESS" | "DONE";
export type Priority = "LOW" | "MEDIUM" | "HIGH";

export interface Task {
  id: string;
  title: string;
  description: string | null;
  priority: Priority;
  status: TaskStatus;
  dueDate: string | null;
  tags: string[];
  assigneeId: string | null;
  ownerId: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
}

export interface Reminder {
  scheduled: boolean;
  job: {
    type: string;
    status: string;
    retryCount: number;
    maxRetries: number;
    nextRunAt: string;
    lastError: string | null;
    lastAttempt: {
      status: string;
      startedAt: string;
      finishedAt: string | null;
      errorMessage: string | null;
    } | null;
  } | null;
}

export class ApiError extends Error {
  code: string;
  status: number;
  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

function tokens() {
  return {
    access: localStorage.getItem("tf_access"),
    refresh: localStorage.getItem("tf_refresh"),
  };
}

export function saveTokens(access: string, refresh: string) {
  localStorage.setItem("tf_access", access);
  localStorage.setItem("tf_refresh", refresh);
}

export function clearTokens() {
  localStorage.removeItem("tf_access");
  localStorage.removeItem("tf_refresh");
}

async function raw(path: string, init: RequestInit, token?: string | null) {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(init.headers as Record<string, string>),
  };
  if (token) headers["Authorization"] = `Bearer ${token}`;
  const res = await fetch(BASE + path, { ...init, headers });
  if (res.status === 204) return null;
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new ApiError(res.status, body.code ?? "ERROR", body.message ?? "Request failed.");
  }
  return body;
}

export async function api(path: string, init: RequestInit = {}) {
  const t = tokens();
  try {
    return await raw(path, init, t.access);
  } catch (e) {
    if (e instanceof ApiError && e.status === 401 && t.refresh) {
      // Single refresh retry.
      const rotated = await raw(
        "/api/v1/auth/refresh",
        { method: "POST", body: JSON.stringify({ refreshToken: t.refresh }) },
        null
      );
      saveTokens(rotated.accessToken, rotated.refreshToken);
      return await raw(path, init, rotated.accessToken);
    }
    throw e;
  }
}

export const Auth = {
  async register(email: string, password: string, displayName: string) {
    const r = await raw(
      "/api/v1/auth/register",
      { method: "POST", body: JSON.stringify({ email, password, displayName }) },
      null
    );
    saveTokens(r.accessToken, r.refreshToken);
    return r;
  },
  async login(email: string, password: string) {
    const r = await raw(
      "/api/v1/auth/login",
      { method: "POST", body: JSON.stringify({ email, password }) },
      null
    );
    saveTokens(r.accessToken, r.refreshToken);
    return r;
  },
  me() {
    return api("/api/v1/auth/me");
  },
};

export const Tasks = {
  list(params: Record<string, string> = {}): Promise<Page<Task>> {
    const q = new URLSearchParams({ size: "100", ...params }).toString();
    return api(`/api/v1/tasks?${q}`);
  },
  create(data: Partial<Task>): Promise<Task> {
    return api("/api/v1/tasks", { method: "POST", body: JSON.stringify(data) });
  },
  update(id: string, data: object): Promise<Task> {
    return api(`/api/v1/tasks/${id}`, { method: "PUT", body: JSON.stringify(data) });
  },
  move(id: string, status: TaskStatus, version: number): Promise<Task> {
    return api(`/api/v1/tasks/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status, version }),
    });
  },
  remove(id: string) {
    return api(`/api/v1/tasks/${id}`, { method: "DELETE" });
  },
  history(id: string) {
    return api(`/api/v1/tasks/${id}/history?size=50`);
  },
  reminder(id: string): Promise<Reminder> {
    return api(`/api/v1/tasks/${id}/reminder`);
  },
};

export const Dashboard = {
  summary() {
    return api("/api/v1/dashboard/summary");
  },
};

export const Jobs = {
  list(status = "") {
    return api(`/api/v1/jobs?size=50${status ? `&status=${status}` : ""}`);
  },
  executions(id: string) {
    return api(`/api/v1/jobs/${id}/executions?size=20`);
  },
  retry(id: string) {
    return api(`/api/v1/jobs/${id}/retry`, { method: "POST" });
  },
};

export const Notifications = {
  list() {
    return api("/api/v1/notifications?size=20");
  },
  unread(): Promise<{ unread: number }> {
    return api("/api/v1/notifications/unread-count");
  },
  markAll() {
    return api("/api/v1/notifications/read-all", { method: "POST" });
  },
};
