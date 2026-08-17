"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useEffect, useState, type ReactNode } from "react";
import "@/i18n";
import { AuthProvider } from "@/lib/auth";
import { BookProvider } from "@/lib/books";
import { ErrorBoundary } from "@/components/ErrorBoundary";

function registerSW() {
  if (
    typeof navigator !== "undefined" &&
    "serviceWorker" in navigator &&
    process.env.NODE_ENV === "production"
  ) {
    navigator.serviceWorker.register("/sw.js").catch(() => {});
  }
}

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: 1,
            staleTime: 15_000,
            refetchOnWindowFocus: true,
          },
        },
      }),
  );

  useEffect(() => {
    registerSW();
  }, []);

  return (
    <ErrorBoundary>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BookProvider>{children}</BookProvider>
      </AuthProvider>
    </QueryClientProvider>
    </ErrorBoundary>
  );
}
