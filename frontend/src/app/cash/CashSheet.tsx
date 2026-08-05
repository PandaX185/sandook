"use client";

import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useBook } from "@/lib/books";
import { aedToFils, filsToAed, filsToAedWithCurrency, fmtDate, todayISO } from "@/lib/format";
import type { CashDay, CashDayInput } from "@/lib/types";
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorBanner,
  Field,
  Input,
  Spinner,
  Td,
  Th,
  WarningBanner,
} from "@/components/ui";

const EMPTY_FORM = {
  date: todayISO(),
  sales: "",
  extra: "",
  withdraw: "",
  deposit: "",
  depositRemarks: "",
  ref: "",
  notes: "",
};

export function CashSheet() {
  const { selectedBookId, selectedBook } = useBook();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const isEditor = user?.role === "EDITOR";

  const [form, setForm] = useState(EMPTY_FORM);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [warnings, setWarnings] = useState<string[]>([]);

  const { data: days = [], isLoading } = useQuery({
    queryKey: ["cash-days", selectedBookId],
    queryFn: () => api<CashDay[]>(`/api/v1/books/${selectedBookId}/cash-days`),
    enabled: selectedBookId !== null,
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["cash-days", selectedBookId] });
  };

  const saveMutation = useMutation({
    mutationFn: (input: CashDayInput) =>
      editingId
        ? api<CashDay>(
            `/api/v1/books/${selectedBookId}/cash-days/${editingId}`,
            { method: "PUT", body: JSON.stringify(input) },
          )
        : api<CashDay>(`/api/v1/books/${selectedBookId}/cash-days`, {
            method: "POST",
            body: JSON.stringify(input),
          }),
    onSuccess: (saved) => {
      invalidate();
      setWarnings(saved.warnings ?? []);
      setForm(EMPTY_FORM);
      setEditingId(null);
      setError(null);
    },
    onError: (err) => {
      setError(err instanceof ApiError ? err.message : "Save failed");
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) =>
      api<void>(`/api/v1/books/${selectedBookId}/cash-days/${id}`, {
        method: "DELETE",
      }),
    onSuccess: () => {
      invalidate();
      if (editingId !== null) {
        setForm(EMPTY_FORM);
        setEditingId(null);
      }
    },
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setWarnings([]);

    const sales = aedToFils(form.sales) ?? 0;
    const extra = aedToFils(form.extra) ?? 0;
    const withdraw = aedToFils(form.withdraw) ?? 0;
    const deposit = aedToFils(form.deposit) ?? 0;

    saveMutation.mutate({
      date: form.date,
      salesMinor: sales,
      extraMinor: extra,
      withdrawMinor: withdraw,
      depositMinor: deposit,
      depositRemarks: form.depositRemarks.trim() || null,
      ref: form.ref.trim() || null,
      notes: form.notes.trim() || null,
    });
  }

  function startEdit(day: CashDay) {
    setEditingId(day.id);
    setWarnings([]);
    setError(null);
    setForm({
      date: day.date,
      sales: filsToAed(day.salesMinor),
      extra: filsToAed(day.extraMinor),
      withdraw: filsToAed(day.withdrawMinor),
      deposit: filsToAed(day.depositMinor),
      depositRemarks: day.depositRemarks ?? "",
      ref: day.ref ?? "",
      notes: day.notes ?? "",
    });
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function cancelEdit() {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setWarnings([]);
    setError(null);
  }

  if (isLoading) return <Spinner />;
  if (selectedBookId === null) return null;

  const currency = selectedBook?.currencyCode ?? "AED";
  const set = (key: keyof typeof EMPTY_FORM) => (value: string) =>
    setForm((f) => ({ ...f, [key]: value }));

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-stone-900">Daily cash sheet</h1>
        <p className="text-sm text-stone-500">
          {selectedBook?.name} · balance = opening + sales + extra − withdraw −
          deposit
        </p>
      </div>

      {isEditor ? (
        <Card title={editingId ? `Editing ${fmtDate(form.date)}` : "New day entry"}>
          <form onSubmit={onSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
              <Field label="Date">
                <Input
                  type="date"
                  value={form.date}
                  onChange={(e) => set("date")(e.target.value)}
                  required
                />
              </Field>
              <Field label="Sales (AED)">
                <Input
                  type="number"
                  inputMode="decimal"
                  step="0.01"
                  min="0"
                  placeholder="0.00"
                  value={form.sales}
                  onChange={(e) => set("sales")(e.target.value)}
                />
              </Field>
              <Field label="Extra (AED)">
                <Input
                  type="number"
                  inputMode="decimal"
                  step="0.01"
                  min="0"
                  placeholder="0.00"
                  value={form.extra}
                  onChange={(e) => set("extra")(e.target.value)}
                />
              </Field>
              <Field label="Withdraw (AED)">
                <Input
                  type="number"
                  inputMode="decimal"
                  step="0.01"
                  min="0"
                  placeholder="0.00"
                  value={form.withdraw}
                  onChange={(e) => set("withdraw")(e.target.value)}
                />
              </Field>
              <Field label="Deposit (AED)">
                <Input
                  type="number"
                  inputMode="decimal"
                  step="0.01"
                  min="0"
                  placeholder="0.00"
                  value={form.deposit}
                  onChange={(e) => set("deposit")(e.target.value)}
                />
              </Field>
              <Field label="Deposit remarks">
                <Input
                  value={form.depositRemarks}
                  onChange={(e) => set("depositRemarks")(e.target.value)}
                  placeholder="Bank transfer ref…"
                />
              </Field>
              <Field label="Ref">
                <Input
                  value={form.ref}
                  onChange={(e) => set("ref")(e.target.value)}
                  placeholder="Invoice / note ref"
                />
              </Field>
              <Field label="Notes">
                <Input
                  value={form.notes}
                  onChange={(e) => set("notes")(e.target.value)}
                  placeholder="Anything worth remembering"
                />
              </Field>
            </div>

            {error ? <ErrorBanner message={error} /> : null}
            {warnings.map((w) => (
              <WarningBanner key={w} message={w} />
            ))}

            <div className="flex gap-2">
              <Button type="submit" disabled={saveMutation.isPending}>
                {saveMutation.isPending
                  ? "Saving…"
                  : editingId
                    ? "Save changes"
                    : "Add day"}
              </Button>
              {editingId ? (
                <Button type="button" variant="secondary" onClick={cancelEdit}>
                  Cancel
                </Button>
              ) : null}
            </div>
          </form>
        </Card>
      ) : null}

      <Card title="History">
        {days.length === 0 ? (
          <EmptyState>
            No entries yet{isEditor ? " — add the first day above" : ""}.
          </EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] border-collapse">
              <thead className="border-b border-stone-200">
                <tr>
                  <Th>Date</Th>
                  <Th>Sales</Th>
                  <Th>Extra</Th>
                  <Th>Withdraw</Th>
                  <Th>Deposit</Th>
                  <Th>Net</Th>
                  <Th>Balance</Th>
                  {isEditor ? <Th /> : null}
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {days.map((day) => (
                  <tr key={day.id} className="hover:bg-stone-50">
                    <Td className="font-medium text-stone-900">
                      {fmtDate(day.date)}
                      {day.depositMinor > 0 ? (
                        <span className="ml-1.5">
                          <Badge tone="amber">deposit</Badge>
                        </span>
                      ) : null}
                    </Td>
                    <Td>{filsToAed(day.salesMinor)}</Td>
                    <Td>{filsToAed(day.extraMinor)}</Td>
                    <Td>{filsToAed(day.withdrawMinor)}</Td>
                    <Td>{filsToAed(day.depositMinor)}</Td>
                    <Td className={day.netCashMinor < 0 ? "text-red-600" : ""}>
                      {filsToAed(day.netCashMinor)}
                    </Td>
                    <Td
                      className={`font-semibold ${
                        day.balanceMinor < 0 ? "text-red-600" : "text-emerald-700"
                      }`}
                    >
                      {filsToAed(day.balanceMinor)}
                    </Td>
                    {isEditor ? (
                      <Td>
                        <div className="flex justify-end gap-1">
                          <Button
                            variant="ghost"
                            className="!px-2 !py-1"
                            onClick={() => startEdit(day)}
                          >
                            Edit
                          </Button>
                          <Button
                            variant="ghost"
                            className="!px-2 !py-1"
                            disabled={deleteMutation.isPending}
                            onClick={() => {
                              if (
                                confirm(
                                  `Delete cash day ${fmtDate(day.date)}?`,
                                )
                              ) {
                                deleteMutation.mutate(day.id);
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
        {days.length > 0 ? (
          <p className="mt-3 text-right text-sm font-semibold text-stone-700">
            Balance {filsToAedWithCurrency(days[days.length - 1].balanceMinor, currency)}
          </p>
        ) : null}
      </Card>
    </div>
  );
}
