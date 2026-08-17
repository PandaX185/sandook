"use client";

import { RequireAuth } from "@/components/RequireAuth";
import { AppShell } from "@/components/AppShell";
import { usePageTitle } from "@/lib/usePageTitle";
import { Audit } from "./Audit";

export default function AuditPage() {
  usePageTitle("nav.audit");
  return (
    <RequireAuth>
      <AppShell>
        <Audit />
      </AppShell>
    </RequireAuth>
  );
}
