"use client";

import { RequireAuth } from "@/components/RequireAuth";
import { AppShell } from "@/components/AppShell";
import { PettyCash } from "./PettyCash";

export default function PettyCashPage() {
  return (
    <RequireAuth>
      <AppShell>
        <PettyCash />
      </AppShell>
    </RequireAuth>
  );
}
