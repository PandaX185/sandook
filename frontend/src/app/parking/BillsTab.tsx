"use client";

import { Fragment, useEffect, useMemo, useState, type FormEvent } from "react";
import { keepPreviousData, useMutation, useQuery } from "@tanstack/react-query";

import { useTranslation } from "react-i18next";
import { api, ApiError } from "@/lib/api";
import {
  aedToFils,
  filsToAed,
  filsToAedWithCurrency,
  fmtDate,
  todayISO,
} from "@/lib/format";
import type {
  ParkingBill,
  ParkingBillInput,
  ParkingBillSummary,
  PaymentMethod,
} from "@/lib/types";
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Field,
  Input,
  Select,
  Spinner,
  StatCard,
  Td,
  Th,
} from "@/components/ui";

function useDebouncedValue<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}

const EMPTY_BILL = { plateNo: "", amount: "", date: todayISO() };

const PRESETS = [
  { key: "today" },
  { key: "yesterday" },
  { key: "week" },
  { key: "month" },
  { key: "all" },
] as const;

function presetRange(key: (typeof PRESETS)[number]["key"]): { from: string; to: string } {
  const now = new Date();
  const iso = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  switch (key) {
    case "today":
      return { from: iso(now), to: iso(now) };
    case "yesterday": {
      const y = new Date(now);
      y.setDate(y.getDate() - 1);
      return { from: iso(y), to: iso(y) };
    }
    case "week": {
      const monday = new Date(now);
      monday.setDate(now.getDate() - ((now.getDay() + 6) % 7));
      return { from: iso(monday), to: iso(now) };
    }
    case "month": {
      const first = new Date(now.getFullYear(), now.getMonth(), 1);
      return { from: iso(first), to: iso(now) };
    }
    case "all":
      return { from: "", to: "" };
  }
}

export function BillsTab({
  bookId,
  currency,
  isEditor,
  invalidate,
  onError,
  onFilters,
}: {
  bookId: number;
  currency: string;
  isEditor: boolean;
  invalidate: () => void;
  onError: (msg: string | null) => void;
  onFilters: (f: { from: string; to: string }) => void;
}) {
  const [form, setForm] = useState(EMPTY_BILL);
  const [payment, setPayment] = useState<PaymentMethod>("CASH");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [plate, setPlate] = useState("");
  const [methodFilter, setMethodFilter] = useState<"" | PaymentMethod>("");
  const debouncedPlate = useDebouncedValue(plate, 300);
  const { t } = useTranslation();

  useEffect(() => {
    onFilters({ from, to });
  }, [from, to, onFilters]);

  const billsQuery = useQuery({
    queryKey: ["parking-bills", bookId, from, to, debouncedPlate, methodFilter],
    queryFn: () => {
      const params = new URLSearchParams();
      if (from) params.set("from", from);
      if (to) params.set("to", to);
      if (debouncedPlate) params.set("plate", debouncedPlate);
      if (methodFilter) params.set("paymentMethod", methodFilter);
      const qs = params.toString();
      return api<ParkingBill[]>(`/api/v1/books/${bookId}/parking/bills${qs ? `?${qs}` : ""}`);
    },
    placeholderData: keepPreviousData,
  });

  const summaryQuery = useQuery({
    queryKey: ["parking-summary", bookId, from, to, methodFilter],
    queryFn: () => {
      const params = new URLSearchParams();
      if (from) params.set("from", from);
      if (to) params.set("to", to);
      if (methodFilter) params.set("paymentMethod", methodFilter);
      const qs = params.toString();
      return api<ParkingBillSummary>(`/api/v1/books/${bookId}/parking/bills/summary${qs ? `?${qs}` : ""}`);
    },
    placeholderData: keepPreviousData,
  });

  const saveMutation = useMutation({
    mutationFn: (input: ParkingBillInput) =>
      editingId
        ? api<ParkingBill>(`/api/v1/books/${bookId}/parking/bills/${editingId}`, {
            method: "PUT",
            body: JSON.stringify(input),
          })
        : api<ParkingBill>(`/api/v1/books/${bookId}/parking/bills`, {
            method: "POST",
            body: JSON.stringify(input),
          }),
    onSuccess: () => {
      invalidate();
      setForm(EMPTY_BILL);
      setEditingId(null);
      onError(null);
    },
    onError: (err) => onError(err instanceof ApiError ? err.message : t("common.saveFailed")),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) =>
      api<void>(`/api/v1/books/${bookId}/parking/bills/${id}`, { method: "DELETE" }),
    onSuccess: () => {
      invalidate();
      if (editingId !== null) {
        setForm(EMPTY_BILL);
        setEditingId(null);
      }
    },
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const amount = aedToFils(form.amount);
    if (amount === null || amount <= 0) {
      onError(t("common.enterAmountGreaterThanZero"));
      return;
    }
    saveMutation.mutate({
      plateNo: form.plateNo.trim(),
      amountMinor: amount,
      paymentMethod: payment,
      billedAt: form.date,
    });
  }

  function startEdit(bill: ParkingBill) {
    setEditingId(bill.id);
    setPayment(bill.paymentMethod);
    setForm({
      plateNo: bill.plateNo,
      amount: filsToAed(bill.amountMinor),
      date: bill.billedAt,
    });
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  const bills = billsQuery.data ?? [];
  const summary = summaryQuery.data;

  const dayGroups = useMemo(() => {
    const map = new Map<string, ParkingBill[]>();
    for (const bill of billsQuery.data ?? []) {
      const arr = map.get(bill.billedAt) ?? [];
      arr.push(bill);
      map.set(bill.billedAt, arr);
    }
    return [...map.entries()].sort((a, b) => b[0].localeCompare(a[0]));
  }, [billsQuery.data]);

  const isLoading = billsQuery.isLoading || summaryQuery.isLoading;

  if (isLoading) return <Spinner />;

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label={t("parking.cashFiltered")} value={filsToAedWithCurrency(summary?.cashMinor ?? 0, currency)} tone="green" />
        <StatCard label={t("parking.cardFiltered")} value={filsToAedWithCurrency(summary?.cardMinor ?? 0, currency)} />
        <StatCard label={t("parking.total")} value={filsToAedWithCurrency(summary?.totalMinor ?? 0, currency)} />
        <StatCard label={t("parking.bills")} value={String(summary?.count ?? 0)} />
      </div>

      <Card title={editingId ? t("parking.editBill") : t("parking.newBill")}>
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-3 sm:flex sm:flex-wrap sm:items-end">
            <div className="w-full sm:w-40">
              <Field label={t("parking.plateNo")}>
                <Input
                  value={form.plateNo}
                  onChange={(e) => setForm((f) => ({ ...f, plateNo: e.target.value }))}
                  placeholder="e.g. 12345"
                  required
                />
              </Field>
            </div>
            <div className="w-full sm:w-40">
              <Field label={t("parking.amountAed")}>
                <Input
                  type="number"
                  inputMode="decimal"
                  step="0.01"
                  min="0"
                  placeholder="0.00"
                  value={form.amount}
                  onChange={(e) => setForm((f) => ({ ...f, amount: e.target.value }))}
                  required
                />
              </Field>
            </div>
            <div className="w-full sm:w-36">
              <Field label={t("common.date")}>
                <Input
                  type="date"
                  value={form.date}
                  onChange={(e) => setForm((f) => ({ ...f, date: e.target.value }))}
                  required
                />
              </Field>
            </div>
            <div className="col-span-2 flex overflow-hidden rounded-lg border border-stone-300 sm:col-span-auto">
              <button
                type="button"
                onClick={() => setPayment("CASH")}
                className={`flex-1 px-4 py-2 text-sm font-medium transition sm:flex-initial ${
                  payment === "CASH"
                    ? "bg-emerald-600 text-white"
                    : "bg-white text-stone-600 hover:bg-stone-50"
                }`}
              >
                {t("payment.CASH")}
              </button>
              <button
                type="button"
                onClick={() => setPayment("CARD")}
                className={`flex-1 px-4 py-2 text-sm font-medium transition sm:flex-initial ${
                  payment === "CARD"
                    ? "bg-emerald-600 text-white"
                    : "bg-white text-stone-600 hover:bg-stone-50"
                }`}
              >
                {t("payment.CARD")}
              </button>
            </div>
            <div className="col-span-2 flex gap-2 sm:col-span-auto">
              <Button type="submit" disabled={saveMutation.isPending}>
                {saveMutation.isPending ? t("common.saving") : editingId ? t("common.saveChanges") : t("parking.addBill")}
              </Button>
              {editingId ? (
                <Button type="button" variant="secondary" onClick={() => { setEditingId(null); setForm(EMPTY_BILL); }}>
                  {t("common.cancel")}
                </Button>
              ) : null}
            </div>
          </div>
        </form>
      </Card>

      <Card title={t("parking.bills")}>
        <div className="mb-3 grid grid-cols-2 gap-3 sm:flex sm:flex-wrap sm:items-end">
          <div className="w-full sm:w-36">
            <Field label={t("common.from")}>
              <Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
            </Field>
          </div>
          <div className="w-full sm:w-36">
            <Field label={t("common.to")}>
              <Input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
            </Field>
          </div>
          <div className="w-full sm:w-40">
            <Field label={t("parking.plateFilter")}>
              <Input value={plate} onChange={(e) => setPlate(e.target.value)} placeholder={t("parking.anyPlate")} />
            </Field>
          </div>
          <div className="w-full sm:w-32">
            <Field label={t("parking.method")}>
              <Select
                value={methodFilter}
                onChange={(e) => setMethodFilter(e.target.value as "" | PaymentMethod)}
              >
                <option value="">{t("common.all")}</option>
                <option value="CASH">{t("payment.CASH")}</option>
                <option value="CARD">{t("payment.CARD")}</option>
              </Select>
            </Field>
          </div>
          <div className="col-span-2 flex flex-wrap gap-1.5 pb-1 sm:col-span-auto">
            {PRESETS.map((p) => (
              <button
                key={p.key}
                type="button"
                onClick={() => {
                  const r = presetRange(p.key);
                  setFrom(r.from);
                  setTo(r.to);
                }}
                className="rounded-md border border-stone-300 bg-white px-2.5 py-1 text-xs font-medium text-stone-600 transition hover:bg-stone-50"
              >
                {t(`parking.${p.key}`)}
              </button>
            ))}
            <button
              type="button"
              onClick={() => {
                setFrom("");
                setTo("");
                setPlate("");
                setMethodFilter("");
              }}
              className="rounded-md border border-red-200 bg-white px-2.5 py-1 text-xs font-medium text-red-600 transition hover:bg-red-50"
            >
              {t("common.clear")}
            </button>
          </div>
        </div>
        {bills.length === 0 ? (
          <EmptyState>{isEditor ? t("parking.noBillsMatchEditor") : t("parking.noBillsMatch")}</EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[520px] border-collapse">
              <thead className="border-b border-stone-200">
                <tr>
                  <Th>{t("common.date")}</Th>
                  <Th>{t("parking.plate")}</Th>
                  <Th>{t("parking.method")}</Th>
                  <Th>{t("common.amount")}</Th>
                  {isEditor ? <Th /> : null}
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {dayGroups.map(([date, dayBills]) => {
                  const cash = dayBills
                    .filter((b) => b.paymentMethod === "CASH")
                    .reduce((s, b) => s + b.amountMinor, 0);
                  const card = dayBills
                    .filter((b) => b.paymentMethod === "CARD")
                    .reduce((s, b) => s + b.amountMinor, 0);
                  return (
                    <Fragment key={date}>
                      <tr className="bg-stone-100">
                        <td colSpan={isEditor ? 5 : 4} className="px-3 py-2">
                          <span className="font-semibold text-stone-800">{fmtDate(date)}</span>
                          <span className="ms-3 text-xs text-stone-500">
                            {t("payment.CASH").toLowerCase()} {filsToAed(cash)} · {t("payment.CARD").toLowerCase()} {filsToAed(card)} · {t("parking.total").toLowerCase()}{" "}
                            {filsToAed(cash + card)} · {dayBills.length}{" "}
                            {dayBills.length === 1 ? t("parking.bill") : t("parking.bills")}
                          </span>
                        </td>
                      </tr>
                      {dayBills.map((bill) => (
                        <tr key={bill.id} className="hover:bg-stone-50">
                          <Td className="font-medium text-stone-900">{fmtDate(bill.billedAt)}</Td>
                          <Td>{bill.plateNo}</Td>
                          <Td>
                            <Badge tone={bill.paymentMethod === "CASH" ? "green" : "stone"}>
                              {bill.paymentMethod}
                            </Badge>
                          </Td>
                          <Td className="font-semibold">{filsToAed(bill.amountMinor)}</Td>
                          {isEditor ? (
                            <Td>
                              <div className="flex justify-end gap-1">
                                <Button variant="ghost" className="!px-2 !py-1" onClick={() => startEdit(bill)}>
                                  {t("common.edit")}
                                </Button>
                                <Button
                                  variant="ghost"
                                  className="!px-2 !py-1"
                                  disabled={deleteMutation.isPending}
                                  onClick={() => {
                                    if (confirm(t("parking.deleteBillConfirm", { plate: bill.plateNo, amount: filsToAed(bill.amountMinor) }))) {
                                      deleteMutation.mutate(bill.id);
                                    }
                                  }}
                                >
                                  {t("common.delete")}
                                </Button>
                              </div>
                            </Td>
                          ) : null}
                        </tr>
                      ))}
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
