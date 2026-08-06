"use client";

import { RequireAuth } from "@/components/RequireAuth";
import { AppShell } from "@/components/AppShell";
import { Parking } from "./Parking";

export default function ParkingPage() {
  return (
    <RequireAuth>
      <AppShell>
        <Parking />
      </AppShell>
    </RequireAuth>
  );
}
