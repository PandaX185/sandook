"use client";

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8081";

let accessToken: string | null = null;
let onAuthFailure: (() => void) | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}

/** Called when a refresh attempt fails — the auth layer signs the user out. */
export function setOnAuthFailure(fn: (() => void) | null) {
  onAuthFailure = fn;
}

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function parseError(res: Response): Promise<string> {
  try {
    const body = await res.json();
    if (body?.detail) return String(body.detail);
    if (body?.errors) return JSON.stringify(body.errors);
    return `Request failed (${res.status})`;
  } catch {
    return `Request failed (${res.status})`;
  }
}

async function refreshTokens(): Promise<boolean> {
  const refreshToken = localStorage.getItem("sandook.refresh");
  if (!refreshToken) return false;
  try {
    const res = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) return false;
    const data = await res.json();
    accessToken = data.accessToken;
    sessionStorage.setItem("sandook.access", data.accessToken);
    localStorage.setItem("sandook.refresh", data.refreshToken);
    return true;
  } catch {
    return false;
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const isFormData = init.body instanceof FormData;
  const headers: Record<string, string> = {
    // For FormData, omit Content-Type so the browser sets the multipart boundary.
    ...(isFormData ? {} : { "Content-Type": "application/json" }),
    ...(init.headers as Record<string, string> | undefined),
  };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

  const doFetch = () =>
    fetch(`${API_BASE}${path}`, { ...init, headers, cache: "no-store" });

  let res = await doFetch();

  if (res.status === 401 && accessToken && !path.startsWith("/api/v1/auth/")) {
    const refreshed = await refreshTokens();
    if (refreshed) {
      headers.Authorization = `Bearer ${accessToken}`;
      res = await doFetch();
    } else {
      accessToken = null;
      sessionStorage.removeItem("sandook.access");
      onAuthFailure?.();
    }
  }

  if (!res.ok) {
    throw new ApiError(res.status, await parseError(res));
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const apiUrl = (path: string) => `${API_BASE}${path}`;

/** Fetches a binary attachment and triggers a browser download. */
export async function downloadFile(
  path: string,
  filename: string
): Promise<void> {
  const headers: Record<string, string> = {};
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

  const doFetch = () =>
    fetch(`${API_BASE}${path}`, { headers, cache: "no-store" });

  let res = await doFetch();

  if (res.status === 401 && accessToken && !path.startsWith("/api/v1/auth/")) {
    const refreshed = await refreshTokens();
    if (refreshed) {
      headers.Authorization = `Bearer ${accessToken}`;
      res = await doFetch();
    } else {
      accessToken = null;
      sessionStorage.removeItem("sandook.access");
      onAuthFailure?.();
    }
  }

  if (!res.ok) {
    throw new ApiError(res.status, await parseError(res));
  }

  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
