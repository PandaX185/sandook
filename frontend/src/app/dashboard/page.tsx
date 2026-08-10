"use client";

import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
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
  const { t } = useTranslation();

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
          label={t("dashboard.cashInHand")}
          value={filsToAedWithCurrency(currentBalance, currency)}
          sub={
            days.length === 0
              ? t("dashboard.noEntriesYet")
              : `${t("common.date")} ${fmtDate(days[days.length - 1].date)}`
          }
          tone={currentBalance < 0 ? "red" : currentBalance > 0 ? "green" : "default"}
        />
        <StatCard
          label={t("dashboard.todaysNet")}
          value={todayNet === null ? "—" : filsToAedWithCurrency(todayNet, currency)}
          sub={
            todayRow
              ? t("dashboard.salesWithdrawDeposit")
              : t("dashboard.noEntryForTodayYet")
          }
          tone={todayNet !== null && todayNet < 0 ? "red" : "default"}
        />
        <StatCard
          label={t("dashboard.pettyCash")}
          value={filsToAedWithCurrency(pettyBalance, currency)}
          sub={
            lastPetty
              ? `${t("common.date")}: ${t(`directions.${lastPetty.type}`)} ${filsToAedWithCurrency(lastPetty.amountMinor, currency)}`
              : t("dashboard.noTransactionsYet")
          }
          tone={pettyBalance < 0 ? "red" : "default"}
        />
      </div>

      {days.length === 0 ? (
        <EmptyState>
          {t("dashboard.noEntriesYet")}{" "}
          <Link href="/cash" className="inline-flex items-center font-semibold text-emerald-600">
            {t("dashboard.addFirstDay")}
            <ArrowRight className="ms-1 h-4 w-4" aria-hidden />
          </Link>
        </EmptyState>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Link
            href="/cash"
            className="rounded-xl border border-stone-200 bg-white p-5 shadow-sm transition hover:border-emerald-400"
          >
            <p className="font-semibold text-stone-800">{t("dashboard.dailyCashSheet")}</p>
            <p className="mt-1 text-sm text-stone-500">
              {t("dashboard.daysRecorded", { count: days.length })}
            </p>
          </Link>
          <Link
            href="/petty-cash"
            className="rounded-xl border border-stone-200 bg-white p-5 shadow-sm transition hover:border-emerald-400"
          >
            <p className="font-semibold text-stone-800">{t("dashboard.pettyCashLedger")}</p>
            <p className="mt-1 text-sm text-stone-500">{t("pettyCash.topupsLinkAutomatically")}</p>
          </Link>
        </div>
      )}
    </div>
  );
}
