"use client";

import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
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
  ErrorBanner,
  Field,
  Input,
  Select,
  Spinner,
  StatCard,
  Td,
  Th,
  WarningBanner,
} from "@/components/ui";

type Tab = "bills" | "statement" | "bookings";

const MOVE_TYPE_LABEL: Record<ParkingCashMoveType, string> = {
  OPENING: "Opening",
  TRANSFER_TO_SHOP: "→ Shop",
  SALARY: "Salary",
  EXPENSE: "Expense",
  CLOSING: "Closing",
};

const MOVE_TYPE_TONE: Record<ParkingCashMoveType, "green" | "red" | "stone" | "amber"> = {
  OPENING: "green",
  TRANSFER_TO_SHOP: "amber",
  SALARY: "red",
  EXPENSE: "red",
  CLOSING: "stone",
};

export function Parking() {
  const { selectedBookId, selectedBook } = useBook();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const isEditor = user?.role === "EDITOR";

  const [tab, setTab] = useState<Tab>("bills");
  const [error, setError] = useState<string | null>(null);
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
          <h1 className="text-2xl font-bold text-stone-900">Parking</h1>
          <p className="text-sm text-stone-500">{selectedBook?.name} · bills, cash moves &amp; bookings</p>
        </div>
        <div className="flex overflow-hidden rounded-lg border border-stone-300">
          {(
            [
              ["bills", "Bills"],
              ["statement", "Statement"],
              ["bookings", "Bookings"],
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

      {error ? <ErrorBanner message={error} /> : null}

      {bookId === null ? (
        <Spinner />
      ) : tab === "bills" ? (
        <BillsTab bookId={bookId} currency={selectedBook?.currencyCode ?? "AED"} isEditor={isEditor} invalidate={invalidate} onError={setError} />
      ) : tab === "statement" ? (
        <StatementTab bookId={bookId} currency={selectedBook?.currencyCode ?? "AED"} />
      ) : (
        <BookingsTab bookId={bookId} currency={selectedBook?.currencyCode ?? "AED"} isEditor={isEditor} invalidate={invalidate} onError={setError} />
      )}
    </div>
  );
}

// --- Bills ---

const EMPTY_BILL = { plateNo: "", amount: "", date: todayISO() };

function BillsTab({
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
  const [form, setForm] = useState(EMPTY_BILL);
  const [payment, setPayment] = useState<PaymentMethod>("CASH");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [plate, setPlate] = useState("");

  const billsQuery = useQuery({
    queryKey: ["parking-bills", bookId, from, to, plate],
    queryFn: () => {
      const params = new URLSearchParams();
      if (from) params.set("from", from);
      if (to) params.set("to", to);
      if (plate) params.set("plate", plate);
      const qs = params.toString();
      return api<ParkingBill[]>(`/api/v1/books/${bookId}/parking/bills${qs ? `?${qs}` : ""}`);
    },
  });

  const summaryQuery = useQuery({
    queryKey: ["parking-summary", bookId, from, to],
    queryFn: () => {
      const params = new URLSearchParams();
      if (from) params.set("from", from);
      if (to) params.set("to", to);
      const qs = params.toString();
      return api<ParkingBillSummary>(`/api/v1/books/${bookId}/parking/bills/summary${qs ? `?${qs}` : ""}`);
    },
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
    onError: (err) => onError(err instanceof ApiError ? err.message : "Save failed"),
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
      onError("Enter an amount greater than 0");
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
  const isLoading = billsQuery.isLoading || summaryQuery.isLoading;

  if (isLoading) return <Spinner />;

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Cash (filtered)" value={filsToAedWithCurrency(summary?.cashMinor ?? 0, currency)} tone="green" />
        <StatCard label="Card (filtered)" value={filsToAedWithCurrency(summary?.cardMinor ?? 0, currency)} />
        <StatCard label="Total" value={filsToAedWithCurrency(summary?.totalMinor ?? 0, currency)} />
        <StatCard label="Bills" value={String(summary?.count ?? 0)} />
      </div>

      <Card title={editingId ? "Edit bill" : "New bill"}>
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="flex flex-wrap items-end gap-3">
            <div className="w-40">
              <Field label="Plate no.">
                <Input
                  value={form.plateNo}
                  onChange={(e) => setForm((f) => ({ ...f, plateNo: e.target.value }))}
                  placeholder="e.g. 12345"
                  required
                />
              </Field>
            </div>
            <div className="w-40">
              <Field label="Amount (AED)">
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
              <Field label="Date">
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
                {saveMutation.isPending ? "Saving…" : editingId ? "Save changes" : "Add bill"}
              </Button>
              {editingId ? (
                <Button type="button" variant="secondary" onClick={() => { setEditingId(null); setForm(EMPTY_BILL); }}>
                  Cancel
                </Button>
              ) : null}
            </div>
          </div>
        </form>
      </Card>

      <Card title="Bills">
        <div className="mb-3 flex flex-wrap gap-3">
          <div className="w-36">
            <Field label="From">
              <Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
            </Field>
          </div>
          <div className="w-36">
            <Field label="To">
              <Input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
            </Field>
          </div>
          <div className="w-40">
            <Field label="Plate filter">
              <Input value={plate} onChange={(e) => setPlate(e.target.value)} placeholder="Any plate" />
            </Field>
          </div>
        </div>
        {bills.length === 0 ? (
          <EmptyState>No bills match{isEditor ? " — add the first one above" : ""}.</EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[520px] border-collapse">
              <thead className="border-b border-stone-200">
                <tr>
                  <Th>Date</Th>
                  <Th>Plate</Th>
                  <Th>Method</Th>
                  <Th>Amount</Th>
                  {isEditor ? <Th /> : null}
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {bills.map((bill) => (
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
                              if (confirm(`Delete bill for ${bill.plateNo} (${filsToAed(bill.amountMinor)} AED)?`)) {
                                deleteMutation.mutate(bill.id);
                              }
                            }}
                          >
                            Delete
                          </Button>
                        </div>
                      </Td>
                    ) : null}
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

// --- Statement ---

function StatementTab({ bookId, currency }: { bookId: number; currency: string }) {
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");

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
          <Field label="From">
            <Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          </Field>
        </div>
        <div className="w-36">
          <Field label="To">
            <Input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          </Field>
        </div>
      </div>

      {warnings.length > 0 ? (
        <WarningBanner message={warnings.join(" · ")} />
      ) : null}

      <Card title="Daily statement (Excel convention)">
        {statement == null || statement.days.length === 0 ? (
          <EmptyState>No statement rows for this range.</EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] border-collapse">
              <thead className="border-b border-stone-200">
                <tr>
                  <Th>Date</Th>
                  <Th>Opening</Th>
                  <Th>Cash bills</Th>
                  <Th>→ Shop</Th>
                  <Th>Salaries</Th>
                  <Th>Expenses</Th>
                  <Th>Net out</Th>
                  <Th>Closing</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {statement.days.map((day) => (
                  <tr key={day.date} className="hover:bg-stone-50">
                    <Td className="font-medium text-stone-900">{fmtDate(day.date)}</Td>
                    <Td>{filsToAed(day.openingMinor)}</Td>
                    <Td className="text-emerald-700">+{filsToAed(day.cashBillsMinor)}</Td>
                    <Td className="text-amber-700">−{filsToAed(day.transfersToShopMinor)}</Td>
                    <Td className="text-red-600">−{filsToAed(day.salariesMinor)}</Td>
                    <Td className="text-red-600">−{filsToAed(day.expensesMinor)}</Td>
                    <Td>−{filsToAed(day.netOutMinor)}</Td>
                    <Td className="font-semibold">{filsToAed(day.closingMinor)}</Td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card title="Cash moves">
        {moves.length === 0 ? (
          <EmptyState>No cash moves in this range.</EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[560px] border-collapse">
              <thead className="border-b border-stone-200">
                <tr>
                  <Th>Date</Th>
                  <Th>Type</Th>
                  <Th>Description</Th>
                  <Th>Amount</Th>
                  <Th>Balance</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {moves.map((move) => (
                  <tr key={move.id} className="hover:bg-stone-50">
                    <Td className="font-medium text-stone-900">{fmtDate(move.date)}</Td>
                    <Td>
                      <Badge tone={MOVE_TYPE_TONE[move.type]}>{MOVE_TYPE_LABEL[move.type]}</Badge>
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

const EMPTY_BOOKING = { plateNo: "", rate: "", renewalMonth: todayISO() };

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
  const [showActive, setShowActive] = useState<"" | "true" | "false">("");

  const bookingsQuery = useQuery({
    queryKey: ["parking-bookings", bookId, showActive],
    queryFn: () => {
      const qs = showActive ? `?active=${showActive}` : "";
      return api<ParkingBooking[]>(`/api/v1/books/${bookId}/parking/bookings${qs}`);
    },
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
    onError: (err) => onError(err instanceof ApiError ? err.message : "Save failed"),
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

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const rate = aedToFils(form.rate);
    if (rate === null || rate < 0) {
      onError("Enter a valid monthly rate");
      return;
    }
    saveMutation.mutate({
      plateNo: form.plateNo.trim(),
      monthlyRateMinor: rate,
      renewalMonth: form.renewalMonth,
      active,
    });
  }

  function startEdit(booking: ParkingBooking) {
    setEditingId(booking.id);
    setActive(booking.active);
    setForm({
      plateNo: booking.plateNo,
      rate: filsToAed(booking.monthlyRateMinor),
      renewalMonth: booking.renewalMonth,
    });
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  const bookings = bookingsQuery.data ?? [];
  if (bookingsQuery.isLoading) return <Spinner />;

  return (
    <div className="space-y-6">
      {isEditor ? (
        <Card title={editingId ? "Edit booking" : "New booking"}>
          <form onSubmit={onSubmit} className="space-y-4">
            <div className="flex flex-wrap items-end gap-3">
              <div className="w-40">
                <Field label="Plate no.">
                  <Input
                    value={form.plateNo}
                    onChange={(e) => setForm((f) => ({ ...f, plateNo: e.target.value }))}
                    placeholder="e.g. 12345"
                    required
                  />
                </Field>
              </div>
              <div className="w-44">
                <Field label="Monthly rate (AED)">
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
                <Field label="Renewal month">
                  <Input
                    type="month"
                    value={form.renewalMonth}
                    onChange={(e) => setForm((f) => ({ ...f, renewalMonth: e.target.value }))}
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
                Active
              </label>
              <div className="flex gap-2">
                <Button type="submit" disabled={saveMutation.isPending}>
                  {saveMutation.isPending ? "Saving…" : editingId ? "Save changes" : "Add booking"}
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
                    Cancel
                  </Button>
                ) : null}
              </div>
            </div>
          </form>
        </Card>
      ) : null}

      <Card title="Bookings">
        <div className="mb-3 w-44">
          <Field label="Status">
            <Select value={showActive} onChange={(e) => setShowActive(e.target.value as "" | "true" | "false")}>
              <option value="">All</option>
              <option value="true">Active only</option>
              <option value="false">Inactive only</option>
            </Select>
          </Field>
        </div>
        {bookings.length === 0 ? (
          <EmptyState>No bookings{isEditor ? " — add the first one above" : ""}.</EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[560px] border-collapse">
              <thead className="border-b border-stone-200">
                <tr>
                  <Th>Plate</Th>
                  <Th>Monthly rate</Th>
                  <Th>Renewal</Th>
                  <Th>Status</Th>
                  {isEditor ? <Th /> : null}
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {bookings.map((booking) => (
                  <tr key={booking.id} className="hover:bg-stone-50">
                    <Td className="font-medium text-stone-900">{booking.plateNo}</Td>
                    <Td>{filsToAedWithCurrency(booking.monthlyRateMinor, currency)}</Td>
                    <Td>{booking.renewalMonth}</Td>
                    <Td>
                      {booking.due ? (
                        <Badge tone="red">Due</Badge>
                      ) : booking.active ? (
                        <Badge tone="green">Active</Badge>
                      ) : (
                        <Badge tone="stone">Inactive</Badge>
                      )}
                    </Td>
                    {isEditor ? (
                      <Td>
                        <div className="flex justify-end gap-1">
                          <Button variant="ghost" className="!px-2 !py-1" onClick={() => startEdit(booking)}>
                            Edit
                          </Button>
                          <Button
                            variant="ghost"
                            className="!px-2 !py-1"
                            disabled={deleteMutation.isPending}
                            onClick={() => {
                              if (confirm(`Delete booking for ${booking.plateNo}?`)) {
                                deleteMutation.mutate(booking.id);
                              }
                            }}
                          >
                            Delete
                          </Button>
                        </div>
                      </Td>
                    ) : null}
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
