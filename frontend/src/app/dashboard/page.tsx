"use client";

import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { RequireAuth } from "@/components/RequireAuth";
import { AppShell } from "@/components/AppShell";
import { EmptyState, Spinner, StatCard } from "@/components/ui";
import { api } from "@/lib/api";
import { useBook } from "@/lib/books";
import { filsToAedWithCurrency, fmtDate, todayISO } from "@/lib/format";
import type { CashDay, PettyCashTx } from "@/lib/types";
import { ParkingNotifications } from "@/app/parking/ParkingNotifications";

export default function DashboardPage() {
  return (
    <RequireAuth>
      <AppShell>
        <Dashboard />
      </AppShell>
    </RequireAuth>
  );
}

function Dashboard() {
  const { selectedBook, selectedBookId } = useBook();

  const cashQuery = useQuery({
    queryKey: ["cash-days", selectedBookId],
    queryFn: () => api<CashDay[]>(`/api/v1/books/${selectedBookId}/cash-days`),
    enabled: selectedBookId !== null,
  });

  const pettyQuery = useQuery({
    queryKey: ["petty-balance", selectedBookId],
    queryFn: () =>
      api<{ bookId: number; balanceMinor: number }>(
        `/api/v1/books/${selectedBookId}/petty-cash/balance`,
      ),
    enabled: selectedBookId !== null,
  });

  const pettyTxQuery = useQuery({
    queryKey: ["petty-cash", selectedBookId],
    queryFn: () =>
      api<PettyCashTx[]>(`/api/v1/books/${selectedBookId}/petty-cash/transactions`),
    enabled: selectedBookId !== null,
  });

  if (!selectedBook) return <Spinner />;

  const days = cashQuery.data ?? [];
  const today = todayISO();
  const currentBalance = days.length > 0 ? days[days.length - 1].balanceMinor : 0;
  const todayRow = days.find((d) => d.date === today);
  const todayNet = todayRow ? todayRow.netCashMinor : null;
  const pettyBalance = pettyQuery.data?.balanceMinor ?? 0;
  const lastPetty = pettyTxQuery.data?.length
    ? pettyTxQuery.data[pettyTxQuery.data.length - 1]
    : null;

  const currency = selectedBook.currencyCode;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-stone-900">{selectedBook.name}</h1>
        <p className="text-sm text-stone-500">
          {fmtDate(today)} · {currency}
        </p>
      </div>

      <ParkingNotifications bookId={selectedBookId} />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard
          label="Cash in hand"
          value={filsToAedWithCurrency(currentBalance, currency)}
          sub={
            days.length === 0
              ? "No entries yet"
              : `Last entry ${fmtDate(days[days.length - 1].date)}`
          }
          tone={currentBalance < 0 ? "red" : currentBalance > 0 ? "green" : "default"}
        />
        <StatCard
          label="Today's net"
          value={
            todayNet === null
              ? "—"
              : filsToAedWithCurrency(todayNet, currency)
          }
          sub={todayRow ? "Sales − withdraw − deposit" : "No entry for today yet"}
          tone={todayNet !== null && todayNet < 0 ? "red" : "default"}
        />
        <StatCard
          label="Petty cash"
          value={filsToAedWithCurrency(pettyBalance, currency)}
          sub={
            lastPetty
              ? `Last: ${lastPetty.type} ${filsToAedWithCurrency(lastPetty.amountMinor, currency)}`
              : "No transactions yet"
          }
          tone={pettyBalance < 0 ? "red" : "default"}
        />
      </div>

      {days.length === 0 ? (
        <EmptyState>
          No cash sheet entries for this book yet.{" "}
          <Link href="/cash" className="inline-flex items-center font-semibold text-emerald-600">
            Add the first day
            <ArrowRight className="ml-1 h-4 w-4" aria-hidden />
          </Link>
        </EmptyState>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Link
            href="/cash"
            className="rounded-xl border border-stone-200 bg-white p-5 shadow-sm transition hover:border-emerald-400"
          >
            <p className="font-semibold text-stone-800">Daily cash sheet</p>
            <p className="mt-1 text-sm text-stone-500">
              {days.length} day{days.length === 1 ? "" : "s"} recorded · enter
              sales, withdrawals & deposits
            </p>
          </Link>
          <Link
            href="/petty-cash"
            className="rounded-xl border border-stone-200 bg-white p-5 shadow-sm transition hover:border-emerald-400"
          >
            <p className="font-semibold text-stone-800">Petty cash ledger</p>
            <p className="mt-1 text-sm text-stone-500">
              Top-ups link automatically to the cash sheet withdrawal
            </p>
          </Link>
        </div>
      )}
    </div>
  );
}
