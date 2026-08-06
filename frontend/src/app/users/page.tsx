"use client";

import { RequireAuth } from "@/components/RequireAuth";
import { AppShell } from "@/components/AppShell";
import { Users } from "./Users";

export default function UsersPage() {
  return (
    <RequireAuth>
      <AppShell>
        <Users />
      </AppShell>
    </RequireAuth>
  );
}
