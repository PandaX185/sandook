"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api, setAccessToken, setOnAuthFailure } from "./api";
import type { LoginResponse, Me } from "./types";

type AuthStatus = "loading" | "authed" | "guest";

interface AuthContextValue {
  status: AuthStatus;
  user: Me | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [user, setUser] = useState<Me | null>(null);

  const applySession = useCallback((data: LoginResponse) => {
    setAccessToken(data.accessToken);
    sessionStorage.setItem("sandook.access", data.accessToken);
    localStorage.setItem("sandook.refresh", data.refreshToken);
  }, []);

  const clearSession = useCallback(() => {
    setAccessToken(null);
    sessionStorage.removeItem("sandook.access");
    localStorage.removeItem("sandook.refresh");
    setUser(null);
    setStatus("guest");
  }, []);

  const loadMe = useCallback(async () => {
    const me = await api<Me>("/api/v1/users/me");
    setUser(me);
    setStatus("authed");
  }, []);

  // Bootstrap: restore session from storage on first paint.
  useEffect(() => {
    let cancelled = false;

    async function bootstrap() {
      const stored = sessionStorage.getItem("sandook.access");
      if (!stored) {
        setStatus("guest");
        return;
      }
      setAccessToken(stored);
      try {
        await loadMe();
      } catch {
        // api() already tried refreshing once; if that failed it cleared the
        // session. If it still fails, treat as signed out rather than half-authed.
        if (!cancelled) clearSession();
      }
      if (cancelled) return;
    }

    bootstrap();
    setOnAuthFailure(() => clearSession());
    return () => {
      cancelled = true;
      setOnAuthFailure(null);
    };
  }, [clearSession, loadMe]);

  const login = useCallback(
    async (username: string, password: string) => {
      const data = await api<LoginResponse>("/api/v1/auth/login", {
        method: "POST",
        body: JSON.stringify({ username, password }),
      });
      applySession(data);
      await loadMe();
    },
    [applySession, loadMe],
  );

  const logout = useCallback(async () => {
    const refreshToken = localStorage.getItem("sandook.refresh");
    try {
      if (refreshToken) {
        await api("/api/v1/auth/logout", {
          method: "POST",
          body: JSON.stringify({ refreshToken }),
        });
      }
    } catch {
      // even if the server call fails, sign out locally
    }
    clearSession();
  }, [clearSession]);

  const value = useMemo(
    () => ({ status, user, login, logout }),
    [status, user, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
