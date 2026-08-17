"use client";

import { RequireAuth } from "@/components/RequireAuth";
import { AppShell } from "@/components/AppShell";
import { usePageTitle } from "@/lib/usePageTitle";
import { Parking } from "./Parking";

export default function ParkingPage() {
  usePageTitle("nav.parking");
  return (
    <RequireAuth>
      <AppShell>
        <Parking />
      </AppShell>
    </RequireAuth>
  );
}
