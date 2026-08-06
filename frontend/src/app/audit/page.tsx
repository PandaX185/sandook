"use client";

import { RequireAuth } from "@/components/RequireAuth";
import { AppShell } from "@/components/AppShell";
import { Audit } from "./Audit";

export default function AuditPage() {
  return (
    <RequireAuth>
      <AppShell>
        <Audit />
      </AppShell>
    </RequireAuth>
  );
}
