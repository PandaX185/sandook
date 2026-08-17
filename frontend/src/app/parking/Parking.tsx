"use client";

import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";

import { useTranslation } from "react-i18next";
import { useAuth } from "@/lib/auth";
import { useBook } from "@/lib/books";
import { Spinner } from "@/components/ui";
import { ParkingNotifications } from "./ParkingNotifications";
import { BillsTab } from "./BillsTab";
import { StatementTab } from "./StatementTab";
import { BookingsTab } from "./BookingsTab";


type Tab = "bills" | "statement" | "bookings";

export function Parking() {
  const { selectedBookId, selectedBook } = useBook();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const isEditor = user?.role === "EDITOR";
  const { t } = useTranslation();

  const [tab, setTab] = useState<Tab>("bills");
  const bookId = selectedBookId;

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["parking-bills", bookId] });
    queryClient.invalidateQueries({ queryKey: ["parking-summary", bookId] });
    queryClient.invalidateQueries({ queryKey: ["parking-statement", bookId] });
    queryClient.invalidateQueries({ queryKey: ["parking-bookings", bookId] });
    queryClient.invalidateQueries({ queryKey: ["parking-moves", bookId] });
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-stone-900">{t("nav.parking")}</h1>
          <p className="text-sm text-stone-500">{selectedBook?.name} · {t("parking.subtitle")}</p>
        </div>
        <div className="flex overflow-hidden rounded-lg border border-stone-300">
          {(
            [
              ["bills", t("parking.bills")],
              ["statement", t("parking.statement")],
              ["bookings", t("parking.bookings")],
            ] as [Tab, string][]
          ).map(([key, label]) => (
            <button
              key={key}
              type="button"
              onClick={() => setTab(key)}
              className={`px-4 py-2 text-sm font-medium transition ${
                tab === key
                  ? "bg-emerald-600 text-white"
                  : "bg-white text-stone-600 hover:bg-stone-50"
              }`}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {bookId === null ? null : <ParkingNotifications bookId={bookId} />}

      {bookId === null ? (
        <Spinner />
      ) : tab === "bills" ? (
        <BillsTab bookId={bookId} currency={selectedBook?.currencyCode ?? "AED"} isEditor={isEditor} invalidate={invalidate} onError={() => {}} onFilters={() => {}} />
      ) : tab === "statement" ? (
        <StatementTab bookId={bookId} currency={selectedBook?.currencyCode ?? "AED"} isEditor={isEditor} invalidate={invalidate} onError={() => {}} onFilters={() => {}} />
      ) : (
        <BookingsTab bookId={bookId} currency={selectedBook?.currencyCode ?? "AED"} isEditor={isEditor} invalidate={invalidate} onError={() => {}} />
      )}

    </div>
  );
}
