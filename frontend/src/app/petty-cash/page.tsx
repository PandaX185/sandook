"use client";

import { RequireAuth } from "@/components/RequireAuth";
import { AppShell } from "@/components/AppShell";
import { usePageTitle } from "@/lib/usePageTitle";
import { PettyCash } from "./PettyCash";

export default function PettyCashPage() {
  usePageTitle("nav.pettyCash");
  return (
    <RequireAuth>
      <AppShell>
        <PettyCash />
      </AppShell>
    </RequireAuth>
  );
}
