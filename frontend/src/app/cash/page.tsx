"use client";

import { RequireAuth } from "@/components/RequireAuth";
import { AppShell } from "@/components/AppShell";
import { usePageTitle } from "@/lib/usePageTitle";
import { CashSheet } from "./CashSheet";

export default function CashPage() {
  usePageTitle("nav.cashSheet");
  return (
    <RequireAuth>
      <AppShell>
        <CashSheet />
      </AppShell>
    </RequireAuth>
  );
}
