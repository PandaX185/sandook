"use client";

import { RequireAuth } from "@/components/RequireAuth";
import { AppShell } from "@/components/AppShell";
import { usePageTitle } from "@/lib/usePageTitle";
import { Transfers } from "./Transfers";

export default function TransfersPage() {
  usePageTitle("nav.transfers");
  return (
    <RequireAuth>
      <AppShell>
        <Transfers />
      </AppShell>
    </RequireAuth>
  );
}
