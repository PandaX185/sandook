"use client";

import { RequireAuth } from "@/components/RequireAuth";
import { AppShell } from "@/components/AppShell";
import { usePageTitle } from "@/lib/usePageTitle";
import { Users } from "./Users";

export default function UsersPage() {
  usePageTitle("nav.users");
  return (
    <RequireAuth>
      <AppShell>
        <Users />
      </AppShell>
    </RequireAuth>
  );
}
