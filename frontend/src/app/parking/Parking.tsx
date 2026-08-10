"use client";

import { Fragment, useEffect, useMemo, useState, type FormEvent } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { useTranslation } from "react-i18next";
import { api, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useBook } from "@/lib/books";
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
  ParkingBooking,
  ParkingBookingInput,
  ParkingBookingInterval,
  ParkingBookingPayInput,
  ParkingBookingStatus,
  ParkingCashMove,
  ParkingCashMoveInput,
  ParkingCashMoveType,
  ParkingStatement,
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
  WarningBanner,
} from "@/components/ui";
import { ParkingNotifications } from "./ParkingNotifications";


type Tab = "bills" | "statement" | "bookings";

const MOVE_TYPE_KEY: Record<ParkingCashMoveType, string> = {
  OPENING: "moveTypes.OPENING",
  TRANSFER_TO_SHOP: "parking.toShop",
  SALARY: "moveTypes.SALARY",
  EXPENSE: "moveTypes.EXPENSE",
  CLOSING: "moveTypes.CLOSING",
};

const MOVE_TYPE_TONE: Record<ParkingCashMoveType, "green" | "red" | "stone" | "amber"> = {
  OPENING: "green",
  TRANSFER_TO_SHOP: "amber",
  SALARY: "red",
  EXPENSE: "red",
  CLOSING: "stone",
};

function useDebouncedValue<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}


export function Parking() {
  const { selectedBookId, selectedBook } = useBook();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const isEditor = user?.role === "EDITOR";
  const { t } = useTranslation();

  const [tab, setTab] = useState<Tab>("bills");
  const [error, setError] = useState<string | null>(null);
  const [filters, setFilters] = useState<{ from: string; to: string; year?: string }>({
    from: "",
    to: "",
  });
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
        <BillsTab bookId={bookId} currency={selectedBook?.currencyCode ?? "AED"} isEditor={isEditor} invalidate={invalidate} onError={setError} onFilters={setFilters} />
      ) : tab === "statement" ? (
        <StatementTab bookId={bookId} currency={selectedBook?.currencyCode ?? "AED"} isEditor={isEditor} invalidate={invalidate} onError={setError} onFilters={setFilters} />
      ) : (
        <BookingsTab bookId={bookId} currency={selectedBook?.currencyCode ?? "AED"} isEditor={isEditor} invalidate={invalidate} onError={setError} />
      )}

    </div>
  );
}

// --- Bills ---

const EMPTY_BILL = { plateNo: "", amount: "", date: todayISO() };

const PRESETS = [
  { key: "today", label: "Today" },
  { key: "yesterday", label: "Yesterday" },
  { key: "week", label: "This Week" },
  { key: "month", label: "This Month" },
  { key: "all", label: "All" },
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

function BillsTab({
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
          <div className="flex flex-wrap items-end gap-3">
            <div className="w-40">
              <Field label={t("parking.plateNo")}>
                <Input
                  value={form.plateNo}
                  onChange={(e) => setForm((f) => ({ ...f, plateNo: e.target.value }))}
                  placeholder="e.g. 12345"
                  required
                />
              </Field>
            </div>
            <div className="w-40">
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
            <div className="w-36">
              <Field label={t("common.date")}>
                <Input
                  type="date"
                  value={form.date}
                  onChange={(e) => setForm((f) => ({ ...f, date: e.target.value }))}
                  required
                />
              </Field>
            </div>
            <div className="flex overflow-hidden rounded-lg border border-stone-300">
              <button
                type="button"
                onClick={() => setPayment("CASH")}
                className={`px-4 py-2 text-sm font-medium transition ${
                  payment === "CASH"
                    ? "bg-emerald-600 text-white"
                    : "bg-white text-stone-600 hover:bg-stone-50"
                }`}
              >
                Cash
              </button>
              <button
                type="button"
                onClick={() => setPayment("CARD")}
                className={`px-4 py-2 text-sm font-medium transition ${
                  payment === "CARD"
                    ? "bg-emerald-600 text-white"
                    : "bg-white text-stone-600 hover:bg-stone-50"
                }`}
              >
                Card
              </button>
            </div>
            <div className="flex gap-2">
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
        <div className="mb-3 flex flex-wrap items-end gap-3">
          <div className="w-36">
            <Field label={t("common.from")}>
              <Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
            </Field>
          </div>
          <div className="w-36">
            <Field label={t("common.to")}>
              <Input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
            </Field>
          </div>
          <div className="w-40">
            <Field label={t("parking.plateFilter")}>
              <Input value={plate} onChange={(e) => setPlate(e.target.value)} placeholder={t("parking.anyPlate")} />
            </Field>
          </div>
          <div className="w-32">
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
          <div className="flex flex-wrap gap-1.5 pb-1">
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
                                  Edit
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

const NOTE_CHIP_KEYS: { key: string; value: string }[] = [
  { key: "parking.salary", value: "Salary" },
  { key: "parking.maintenance", value: "Maintenance" },
  { key: "parking.utilities", value: "Utilities" },
  { key: "parking.cleaning", value: "Cleaning" },
  { key: "parking.rent", value: "Rent" },
  { key: "parking.other", value: "Other" },
];

const EMPTY_MOVE = { date: todayISO(), type: "EXPENSE" as ParkingCashMoveType, amount: "", description: "" };

// --- Statement ---

function StatementTab({
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
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [moveForm, setMoveForm] = useState(EMPTY_MOVE);
  const [salaryRows, setSalaryRows] = useState<{ person: string; amount: string }[]>([]);
  const { t } = useTranslation();

  useEffect(() => {
    onFilters({ from, to });
  }, [from, to, onFilters]);

  const createMoveMutation = useMutation({
    mutationFn: (input: ParkingCashMoveInput) =>
      api<ParkingCashMove>(`/api/v1/books/${bookId}/parking/cash-moves`, {
        method: "POST",
        body: JSON.stringify(input),
      }),
    onSuccess: () => {
      invalidate();
      setMoveForm(EMPTY_MOVE);
      setSalaryRows([]);
      onError(null);
    },
    onError: (err) => onError(err instanceof ApiError ? err.message : t("parking.moveFailed")),
  });

  function updateSalaryRow(i: number, key: "person" | "amount", value: string) {
    setSalaryRows((rows) => rows.map((r, j) => (j === i ? { ...r, [key]: value } : r)));
  }

  function onMoveSubmit(e: FormEvent) {
    e.preventDefault();
    const amount = aedToFils(moveForm.amount);
    if (amount === null || amount <= 0) {
      onError(t("common.enterAmountGreaterThanZero"));
      return;
    }
    const needsDescription = moveForm.type === "EXPENSE" || moveForm.type === "SALARY";
    if (needsDescription && !moveForm.description.trim()) {
      onError(t(moveForm.type === "SALARY" ? "parking.salaryMovesNeedNote" : "parking.expenseMovesNeedNote"));
      return;
    }
    let salaryPayments: { person: string; amountMinor: number }[] | undefined;
    if (moveForm.type === "SALARY") {
      const rows = salaryRows
        .filter((r) => r.person.trim() !== "")
        .map((r) => ({ person: r.person.trim(), amountMinor: aedToFils(r.amount) ?? 0 }));
      if (rows.length === 0) {
        onError(t("parking.addAtLeastOneSalaryRow"));
        return;
      }
      const sum = rows.reduce((s, r) => s + r.amountMinor, 0);
      if (sum !== amount) {
        onError(
          t("parking.salarySumMismatch", { sum: filsToAed(sum), amount: filsToAed(amount) }),
        );
        return;
      }
      salaryPayments = rows;
    }
    createMoveMutation.mutate({
      date: moveForm.date,
      type: moveForm.type,
      amountMinor: amount,
      description: needsDescription ? moveForm.description.trim() : null,
      salaryPayments,
    });
  }

  const statementQuery = useQuery({
    queryKey: ["parking-statement", bookId, from, to],
    queryFn: () => {
      const params = new URLSearchParams();
      if (from) params.set("from", from);
      if (to) params.set("to", to);
      const qs = params.toString();
      return api<ParkingStatement>(`/api/v1/books/${bookId}/parking/cash-moves/statement${qs ? `?${qs}` : ""}`);
    },
  });

  const movesQuery = useQuery({
    queryKey: ["parking-moves", bookId, from, to],
    queryFn: () => {
      const params = new URLSearchParams();
      if (from) params.set("from", from);
      if (to) params.set("to", to);
      const qs = params.toString();
      return api<ParkingCashMove[]>(`/api/v1/books/${bookId}/parking/cash-moves${qs ? `?${qs}` : ""}`);
    },
  });

  if (statementQuery.isLoading || movesQuery.isLoading) return <Spinner />;

  const statement = statementQuery.data;
  const moves = movesQuery.data ?? [];
  const warnings = (statement?.days ?? []).flatMap((d) => d.warnings);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end gap-3">
        <div className="w-36">
          <Field label={t("common.from")}>
            <Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          </Field>
        </div>
        <div className="w-36">
          <Field label={t("common.to")}>
            <Input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          </Field>
        </div>
      </div>

      {warnings.length > 0 ? (
        <WarningBanner message={warnings.join(" · ")} />
      ) : null}

      {statement ? (
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <StatCard
            label={t("parking.totalBalance")}
            value={filsToAedWithCurrency(statement.summary.totalBalanceMinor, currency)}
            tone="green"
          />
          <StatCard
            label={t("parking.todayCash")}
            value={
              statement.summary.todayCashMinor == null
                ? "—"
                : filsToAedWithCurrency(statement.summary.todayCashMinor, currency)
            }
          />
          <StatCard
            label={t("parking.todayCard")}
            value={
              statement.summary.todayCardMinor == null
                ? "—"
                : filsToAedWithCurrency(statement.summary.todayCardMinor, currency)
            }
          />
          <StatCard
            label={t("parking.monthBills")}
            value={
              statement.summary.monthBillsMinor == null
                ? "—"
                : filsToAedWithCurrency(statement.summary.monthBillsMinor, currency)
            }
          />
          <StatCard
            label={t("parking.expenses")}
            value={filsToAedWithCurrency(statement.summary.totalExpensesMinor ?? 0, currency)}
            tone="red"
          />
        </div>
      ) : null}

      {isEditor ? (
        <Card title={t("parking.newCashMove")}>
          <form onSubmit={onMoveSubmit} className="space-y-4">
          <div className="flex flex-wrap items-end gap-3">
            <div className="w-40">
              <Field label={t("common.type")}>
                <Select
                  value={moveForm.type}
                  onChange={(e) =>
                    setMoveForm((f) => ({ ...f, type: e.target.value as ParkingCashMoveType }))
                  }
                >
                  <option value="OPENING">{t("moveTypes.OPENING")}</option>
                  <option value="EXPENSE">{t("moveTypes.EXPENSE")}</option>
                  <option value="SALARY">{t("moveTypes.SALARY")}</option>
                  <option value="CLOSING">{t("moveTypes.CLOSING")}</option>
                </Select>
              </Field>
            </div>
            <div className="w-36">
              <Field label={t("common.date")}>
                <Input
                  type="date"
                  value={moveForm.date}
                  onChange={(e) => setMoveForm((f) => ({ ...f, date: e.target.value }))}
                  required
                />
              </Field>
            </div>
            <div className="w-40">
              <Field label={t("parking.amountAed")}>
                <Input
                  type="number"
                  inputMode="decimal"
                  step="0.01"
                  min="0"
                  placeholder="0.00"
                  value={moveForm.amount}
                  onChange={(e) => setMoveForm((f) => ({ ...f, amount: e.target.value }))}
                  required
                />
              </Field>
            </div>
            <Button type="submit" disabled={createMoveMutation.isPending}>
              {createMoveMutation.isPending ? t("common.saving") : t("parking.addMove")}
            </Button>
          </div>

          {moveForm.type === "SALARY" || moveForm.type === "EXPENSE" ? (
            <div className="max-w-xl space-y-2">
              <Field label={t("parking.notesRequired")}>
                <Input
                  value={moveForm.description}
                  onChange={(e) => setMoveForm((f) => ({ ...f, description: e.target.value }))}
                  placeholder={moveForm.type === "SALARY" ? t("parking.weeklySalariesPh") : t("parking.maintenancePh")}
                  required
                />
              </Field>
              <div className="flex flex-wrap gap-1.5">
                {NOTE_CHIP_KEYS.map((chip) => (
                  <button
                    key={chip.key}
                    type="button"
                    onClick={() => setMoveForm((f) => ({ ...f, description: chip.value }))}
                    className="rounded-full border border-stone-300 bg-white px-2.5 py-0.5 text-xs text-stone-600 transition hover:bg-stone-100"
                  >
                    {t(chip.key)}
                  </button>
                ))}
              </div>
            </div>
          ) : null}

          {moveForm.type === "SALARY" ? (
            <div className="max-w-xl space-y-2">
              <p className="text-sm font-medium text-stone-700">{t("parking.salaryPayments")}</p>
              {salaryRows.map((row, i) => (
                <div key={i} className="flex items-end gap-2">
                  <div className="w-44">
                    <Field label={t("parking.person")}>
                      <Input
                        value={row.person}
                        onChange={(e) => updateSalaryRow(i, "person", e.target.value)}
                        placeholder={t("common.name")}
                      />
                    </Field>
                  </div>
                  <div className="w-36">
                    <Field label={t("parking.amountAed")}>
                      <Input
                        type="number"
                        inputMode="decimal"
                        step="0.01"
                        min="0"
                        value={row.amount}
                        onChange={(e) => updateSalaryRow(i, "amount", e.target.value)}
                        placeholder="0.00"
                      />
                    </Field>
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    className="!px-2 !py-1"
                    onClick={() => setSalaryRows((rows) => rows.filter((_, j) => j !== i))}
                  >
                    {t("common.remove")}
                  </Button>
                </div>
              ))}
              <div className="flex items-center gap-3">
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => setSalaryRows((rows) => [...rows, { person: "", amount: "" }])}
                >
                  {t("parking.addRow")}
                </Button>
                {salaryRows.length > 0 ? (
                  <span className="text-xs text-stone-500">
                    {t("parking.sum")} {filsToAed(salaryRows.reduce((s, r) => s + (aedToFils(r.amount) ?? 0), 0))} AED
                  </span>
                ) : null}
              </div>
            </div>
          ) : null}
        </form>
      </Card>
      ) : null}

      <Card title={t("parking.dailyStatement")}>
        {statement == null || statement.days.length === 0 ? (
          <EmptyState>{t("parking.noStatementRows")}</EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[1100px] border-collapse">
              <thead className="border-b border-stone-200">
                <tr>
                  <Th>{t("common.date")}</Th>
                  <Th>{t("parking.opening")}</Th>
                  <Th>{t("payment.CASH")}</Th>
                  <Th>{t("payment.CARD")}</Th>
                  <Th>{t("parking.bookings2")}</Th>
                  <Th>{t("parking.toShop")}</Th>
                  <Th>{t("parking.salaries")}</Th>
                  <Th>{t("parking.expenses")}</Th>
                  <Th>{t("parking.netOut")}</Th>
                  <Th>{t("parking.closing")}</Th>
                  <Th>{t("parking.cumulative")}</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {statement.days.map((day, idx) => (
                  <tr key={day.date} className={`${idx > 0 ? "border-t-2 border-stone-200" : ""} ${idx % 2 === 0 ? "bg-white" : "bg-stone-50/50"} hover:bg-stone-100`}>
                    <Td className="font-medium text-stone-900">{fmtDate(day.date)}</Td>
                    <Td>{filsToAed(day.openingMinor)}</Td>
                    <Td className="text-emerald-700">+{filsToAed(day.cashBillsMinor)}</Td>
                    <Td className="text-emerald-700">+{filsToAed(day.cardBillsMinor)}</Td>
                    <Td className="text-emerald-700">+{filsToAed(day.bookingsMinor)}</Td>
                    <Td className="text-amber-700">−{filsToAed(day.transfersToShopMinor)}</Td>
                    <Td className="text-red-600">−{filsToAed(day.salariesMinor)}</Td>
                    <Td className="text-red-600">
                      −{filsToAed(day.expensesMinor)}
                      {day.expenseNotes.length > 0 ? (
                        <div className="text-xs font-normal text-stone-400">
                          {day.expenseNotes.join(" · ")}
                        </div>
                      ) : null}
                    </Td>
                    <Td>−{filsToAed(day.netOutMinor)}</Td>
                    <Td className="font-semibold">{filsToAed(day.closingMinor)}</Td>
                    <Td>{filsToAed(day.cumulativeMinor)}</Td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card title={t("parking.cashMoves")}>
        {moves.length === 0 ? (
          <EmptyState>{t("parking.noCashMoves")}</EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[560px] border-collapse">
              <thead className="border-b border-stone-200">
                <tr>
                  <Th>{t("common.date")}</Th>
                  <Th>{t("common.type")}</Th>
                  <Th>{t("common.description")}</Th>
                  <Th>{t("common.amount")}</Th>
                  <Th>{t("common.balance")}</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {moves.map((move) => (
                  <tr key={move.id} className="hover:bg-stone-50">
                    <Td className="font-medium text-stone-900">{fmtDate(move.date)}</Td>
                    <Td>
                      <Badge tone={MOVE_TYPE_TONE[move.type]}>{t(MOVE_TYPE_KEY[move.type])}</Badge>
                    </Td>
                    <Td className="whitespace-normal">
                      {move.description ?? ""}
                      {move.salaryPayments.length > 0
                        ? move.salaryPayments.map((p) => `${p.person} ${filsToAed(p.amountMinor)}`).join(" · ")
                        : null}
                    </Td>
                    <Td
                      className={
                        move.type === "OPENING"
                          ? "text-emerald-700"
                          : "text-red-600"
                      }
                    >
                      {move.type === "OPENING" ? "+" : "−"}
                      {filsToAed(move.amountMinor)}
                    </Td>
                    <Td className="font-semibold">{filsToAed(move.balanceMinor)}</Td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}

// --- Bookings ---

const EMPTY_BOOKING = {
  plateNo: "",
  rate: "",
  intervalType: "MONTHLY" as ParkingBookingInterval,
  customMonths: "",
  nextDueDate: todayISO(),
};

function BookingsTab({
  bookId,
  currency,
  isEditor,
  invalidate,
  onError,
}: {
  bookId: number;
  currency: string;
  isEditor: boolean;
  invalidate: () => void;
  onError: (msg: string | null) => void;
}) {
  const [form, setForm] = useState(EMPTY_BOOKING);
  const [active, setActive] = useState(true);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [statusFilter, setStatusFilter] = useState<"" | ParkingBookingStatus>("");
  const [payingId, setPayingId] = useState<number | null>(null);
  const [payForm, setPayForm] = useState({ amount: "", method: "CASH" as PaymentMethod, date: todayISO() });
  const [historyId, setHistoryId] = useState<number | null>(null);
  const { t } = useTranslation();

  const bookingsQuery = useQuery({
    queryKey: ["parking-bookings", bookId, statusFilter],
    queryFn: () => {
      const qs = statusFilter ? `?status=${statusFilter}` : "";
      return api<ParkingBooking[]>(`/api/v1/books/${bookId}/parking/bookings${qs}`);
    },
  });

  const paymentsQuery = useQuery({
    queryKey: ["parking-booking-payments", bookId, historyId],
    queryFn: () =>
      api<ParkingBill[]>(`/api/v1/books/${bookId}/parking/bookings/${historyId}/payments`),
    enabled: historyId !== null,
  });

  const saveMutation = useMutation({
    mutationFn: (input: ParkingBookingInput) =>
      editingId
        ? api<ParkingBooking>(`/api/v1/books/${bookId}/parking/bookings/${editingId}`, {
            method: "PUT",
            body: JSON.stringify(input),
          })
        : api<ParkingBooking>(`/api/v1/books/${bookId}/parking/bookings`, {
            method: "POST",
            body: JSON.stringify(input),
          }),
    onSuccess: () => {
      invalidate();
      setForm(EMPTY_BOOKING);
      setActive(true);
      setEditingId(null);
      onError(null);
    },
    onError: (err) => onError(err instanceof ApiError ? err.message : t("common.saveFailed")),
  });

  const payMutation = useMutation({
    mutationFn: (input: ParkingBookingPayInput) =>
      api<ParkingBooking>(`/api/v1/books/${bookId}/parking/bookings/${payingId}/pay`, {
        method: "POST",
        body: JSON.stringify(input),
      }),
    onSuccess: () => {
      invalidate();
      setPayingId(null);
      onError(null);
    },
    onError: (err) => onError(err instanceof ApiError ? err.message : t("parking.payFailed")),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) =>
      api<void>(`/api/v1/books/${bookId}/parking/bookings/${id}`, { method: "DELETE" }),
    onSuccess: () => {
      invalidate();
      if (editingId !== null) {
        setForm(EMPTY_BOOKING);
        setEditingId(null);
      }
    },
  });

  function intervalMonthsOf(booking: ParkingBooking): number {
    if (booking.intervalType === "CUSTOM") return booking.intervalMonths ?? 1;
    if (booking.intervalType === "THREE_MONTHS") return 3;
    if (booking.intervalType === "SIX_MONTHS") return 6;
    return 1;
  }

  function intervalLabel(interval: ParkingBookingInterval, months: number | null): string {
    switch (interval) {
      case "MONTHLY":
        return t("bookingTerms.MONTHLY");
      case "THREE_MONTHS":
        return t("parking.threeMonths");
      case "SIX_MONTHS":
        return t("parking.sixMonths");
      case "CUSTOM":
        return t("parking.customMonths", { count: months ?? 1 });
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const rate = aedToFils(form.rate);
    if (rate === null || rate < 0) {
      onError(t("parking.enterValidMonthlyRate"));
      return;
    }
    if (form.intervalType === "CUSTOM") {
      const months = Number(form.customMonths);
      if (!Number.isInteger(months) || months < 1 || months > 24) {
        onError(t("parking.customIntervalNeedsMonthCount"));
        return;
      }
    }
    saveMutation.mutate({
      plateNo: form.plateNo.trim(),
      monthlyRateMinor: rate,
      intervalType: form.intervalType,
      intervalMonths: form.intervalType === "CUSTOM" ? Number(form.customMonths) : null,
      nextDueDate: form.nextDueDate,
      active,
    });
  }

  function startEdit(booking: ParkingBooking) {
    setEditingId(booking.id);
    setActive(booking.active);
    setForm({
      plateNo: booking.plateNo,
      rate: filsToAed(booking.monthlyRateMinor),
      intervalType: booking.intervalType,
      customMonths: booking.intervalMonths ? String(booking.intervalMonths) : "",
      nextDueDate: booking.nextDueDate,
    });
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function openPay(booking: ParkingBooking) {
    setPayingId(booking.id);
    setPayForm({
      amount: filsToAed(booking.monthlyRateMinor * intervalMonthsOf(booking)),
      method: "CASH",
      date: todayISO(),
    });
    setHistoryId(null);
  }

  function statusBadge(status: ParkingBookingStatus) {
    switch (status) {
      case "PAID":
        return <Badge tone="green">{t("bookingStatuses.PAID")}</Badge>;
      case "DUE":
        return <Badge tone="amber">{t("bookingStatuses.DUE")}</Badge>;
      case "OVERDUE":
        return <Badge tone="red">{t("bookingStatuses.OVERDUE")}</Badge>;
      default:
        return <Badge tone="stone">{t("bookingStatuses.INACTIVE")}</Badge>;
    }
  }

  const bookings = bookingsQuery.data ?? [];
  if (bookingsQuery.isLoading) return <Spinner />;

  return (
    <div className="space-y-6">
      {isEditor ? (
        <Card title={editingId ? t("parking.editBooking") : t("parking.newBooking")}>
          <form onSubmit={onSubmit} className="space-y-4">
            <div className="flex flex-wrap items-end gap-3">
              <div className="w-40">
                <Field label={t("parking.plateNo")}>
                  <Input
                    value={form.plateNo}
                    onChange={(e) => setForm((f) => ({ ...f, plateNo: e.target.value }))}
                    placeholder="e.g. 12345"
                    required
                  />
                </Field>
              </div>
              <div className="w-44">
                <Field label={t("parking.monthlyRateAed")}>
                  <Input
                    type="number"
                    inputMode="decimal"
                    step="0.01"
                    min="0"
                    placeholder="0.00"
                    value={form.rate}
                    onChange={(e) => setForm((f) => ({ ...f, rate: e.target.value }))}
                    required
                  />
                </Field>
              </div>
              <div className="w-40">
                <Field label={t("parking.interval")}>
                  <Select
                    value={form.intervalType}
                    onChange={(e) =>
                      setForm((f) => ({ ...f, intervalType: e.target.value as ParkingBookingInterval }))
                    }
                  >
                    <option value="MONTHLY">{t("bookingTerms.MONTHLY")}</option>
                    <option value="THREE_MONTHS">{t("parking.threeMonths")}</option>
                    <option value="SIX_MONTHS">{t("parking.sixMonths")}</option>
                    <option value="CUSTOM">{t("parking.custom")}…</option>
                  </Select>
                </Field>
              </div>
              {form.intervalType === "CUSTOM" ? (
                <div className="w-28">
                  <Field label={t("parking.monthsRange")}>
                    <Input
                      type="number"
                      inputMode="numeric"
                      min="1"
                      max="24"
                      placeholder="e.g. 4"
                      value={form.customMonths}
                      onChange={(e) => setForm((f) => ({ ...f, customMonths: e.target.value }))}
                      required
                    />
                  </Field>
                </div>
              ) : null}
              <div className="w-44">
                <Field label={t("parking.nextDueDate")}>
                  <Input
                    type="date"
                    value={form.nextDueDate}
                    onChange={(e) => setForm((f) => ({ ...f, nextDueDate: e.target.value }))}
                    required
                  />
                </Field>
              </div>
              <label className="flex items-center gap-2 text-sm text-stone-700">
                <input
                  type="checkbox"
                  checked={active}
                  onChange={(e) => setActive(e.target.checked)}
                  className="h-4 w-4 rounded border-stone-300 text-emerald-600"
                />
                {t("common.active")}
              </label>
              <div className="flex gap-2">
                <Button type="submit" disabled={saveMutation.isPending}>
                  {saveMutation.isPending ? t("common.saving") : editingId ? t("common.saveChanges") : t("parking.addBooking")}
                </Button>
                {editingId ? (
                  <Button
                    type="button"
                    variant="secondary"
                    onClick={() => {
                      setEditingId(null);
                      setForm(EMPTY_BOOKING);
                      setActive(true);
                    }}
                  >
                    {t("common.cancel")}
                  </Button>
                ) : null}
              </div>
            </div>
          </form>
        </Card>
      ) : null}

      <Card title={t("parking.bookings")}>
        <div className="mb-3 w-44">
          <Field label={t("common.status")}>
            <Select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as "" | ParkingBookingStatus)}
            >
              <option value="">{t("common.all")}</option>
              <option value="PAID">{t("bookingStatuses.PAID")}</option>
              <option value="DUE">{t("bookingStatuses.DUE")}</option>
              <option value="OVERDUE">{t("bookingStatuses.OVERDUE")}</option>
              <option value="INACTIVE">{t("bookingStatuses.INACTIVE")}</option>
            </Select>
          </Field>
        </div>
        {bookings.length === 0 ? (
          <EmptyState>{isEditor ? t("parking.noBookingsEditor") : t("parking.noBookings")}</EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] border-collapse">
              <thead className="border-b border-stone-200">
                <tr>
                  <Th>{t("parking.plate")}</Th>
                  <Th>{t("parking.monthlyRate")}</Th>
                  <Th>{t("parking.interval")}</Th>
                  <Th>{t("parking.nextDueDate")}</Th>
                  <Th>{t("common.status")}</Th>
                  {isEditor ? <Th /> : null}
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {bookings.map((booking) => (
                  <Fragment key={booking.id}>
                    <tr className="hover:bg-stone-50">
                      <Td className="font-medium text-stone-900">{booking.plateNo}</Td>
                      <Td>{filsToAedWithCurrency(booking.monthlyRateMinor, currency)}</Td>
                      <Td>{intervalLabel(booking.intervalType, booking.intervalMonths)}</Td>
                      <Td>
                        {fmtDate(booking.nextDueDate)}
                        {booking.paidThroughDate ? (
                          <div className="text-xs text-stone-400">
                            {t("parking.paidThru")} {fmtDate(booking.paidThroughDate)}
                          </div>
                        ) : null}
                      </Td>
                      <Td>{statusBadge(booking.status)}</Td>
                      {isEditor ? (
                        <Td>
                          <div className="flex justify-end gap-1">
                            <Button variant="ghost" className="!px-2 !py-1" onClick={() => openPay(booking)}>
                              {t("parking.pay")}
                            </Button>
                            <Button
                              variant="ghost"
                              className="!px-2 !py-1"
                              onClick={() => setHistoryId(historyId === booking.id ? null : booking.id)}
                            >
                              {t("parking.payments")}
                            </Button>
                            <Button variant="ghost" className="!px-2 !py-1" onClick={() => startEdit(booking)}>
                              {t("common.edit")}
                            </Button>
                            <Button
                              variant="ghost"
                              className="!px-2 !py-1"
                              disabled={deleteMutation.isPending}
                              onClick={() => {
                                if (confirm(t("parking.deleteBookingConfirm", { plate: booking.plateNo }))) {
                                  deleteMutation.mutate(booking.id);
                                }
                              }}
                            >
                              {t("common.delete")}
                            </Button>
                          </div>
                        </Td>
                      ) : null}
                    </tr>
                    {payingId === booking.id ? (
                      <tr className="bg-stone-50">
                        <td colSpan={isEditor ? 6 : 5} className="px-3 py-3">
                          <form
                            onSubmit={(e) => {
                              e.preventDefault();
                              const amount = aedToFils(payForm.amount);
                              if (amount === null || amount <= 0) {
                                onError(t("parking.enterValidAmount"));
                                return;
                              }
                              payMutation.mutate({
                                amountMinor: amount,
                                paymentMethod: payForm.method,
                                paidAt: payForm.date,
                              });
                            }}
                            className="flex flex-wrap items-end gap-3"
                          >
                            <div className="w-40">
                              <Field label={t("parking.amountAed")}>
                                <Input
                                  type="number"
                                  inputMode="decimal"
                                  step="0.01"
                                  min="0"
                                  placeholder="0.00"
                                  value={payForm.amount}
                                  onChange={(e) => setPayForm((f) => ({ ...f, amount: e.target.value }))}
                                  required
                                />
                              </Field>
                            </div>
                            <div className="w-36">
                              <Field label={t("parking.method")}>
                                <Select
                                  value={payForm.method}
                                  onChange={(e) =>
                                    setPayForm((f) => ({ ...f, method: e.target.value as PaymentMethod }))
                                  }
                                >
                                  <option value="CASH">{t("payment.CASH")}</option>
                                  <option value="CARD">{t("payment.CARD")}</option>
                                </Select>
                              </Field>
                            </div>
                            <div className="w-44">
                              <Field label={t("parking.paidOn")}>
                                <Input
                                  type="date"
                                  value={payForm.date}
                                  onChange={(e) => setPayForm((f) => ({ ...f, date: e.target.value }))}
                                  required
                                />
                              </Field>
                            </div>
                            <Button type="submit" disabled={payMutation.isPending}>
                              {payMutation.isPending ? t("common.saving") : t("parking.confirmPayment")}
                            </Button>
                            <Button type="button" variant="secondary" onClick={() => setPayingId(null)}>
                              {t("common.cancel")}
                            </Button>
                          </form>
                        </td>
                      </tr>
                    ) : null}
                    {historyId === booking.id ? (
                      <tr>
                        <td colSpan={isEditor ? 6 : 5} className="bg-stone-50 px-3 py-3">
                          {paymentsQuery.isLoading ? (
                            <Spinner />
                          ) : (paymentsQuery.data ?? []).length === 0 ? (
                            <p className="text-sm text-stone-500">{t("parking.noPaymentsYet")}</p>
                          ) : (
                            <table className="w-full text-sm">
                              <thead className="border-b border-stone-200">
                                <tr>
                                  <Th>{t("common.date")}</Th>
                                  <Th>{t("parking.method")}</Th>
                                  <Th>{t("common.amount")}</Th>
                                  <Th>{t("parking.enteredBy")}</Th>
                                </tr>
                              </thead>
                              <tbody className="divide-y divide-stone-100">
                                {(paymentsQuery.data ?? []).map((bill) => (
                                  <tr key={bill.id}>
                                    <Td>{fmtDate(bill.billedAt)}</Td>
                                    <Td>{bill.paymentMethod === "CASH" ? t("payment.CASH") : t("payment.CARD")}</Td>
                                    <Td>{filsToAedWithCurrency(bill.amountMinor, currency)}</Td>
                                    <Td>{bill.enteredBy ?? "—"}</Td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          )}
                        </td>
                      </tr>
                    ) : null}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
