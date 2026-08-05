"use client";

import { RequireAuth } from "@/components/RequireAuth";
import { AppShell } from "@/components/AppShell";
import { CashSheet } from "./CashSheet";

export default function CashPage() {
  return (
    <RequireAuth>
      <AppShell>
        <CashSheet />
      </AppShell>
    </RequireAuth>
  );
}
