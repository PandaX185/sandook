"use client";

import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CircleCheck, Info } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useBook } from "@/lib/books";
import { aedToFils, filsToAed, filsToAedWithCurrency, fmtDate, todayISO } from "@/lib/format";
import type { PettyCashInput, PettyCashTx } from "@/lib/types";
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorBanner,
  Field,
  Input,
  Spinner,
  StatCard,
  Td,
  Th,
} from "@/components/ui";

const EMPTY_FORM = {
  date: todayISO(),
  description: "",
  amount: "",
};

export function PettyCash() {
  const { selectedBookId, selectedBook } = useBook();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const isEditor = user?.role === "EDITOR";

  const [form, setForm] = useState(EMPTY_FORM);
  const [type, setType] = useState<"PUT" | "TAKE">("PUT");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [linked, setLinked] = useState<string | null>(null);

  const { data: txs = [], isLoading } = useQuery({
    queryKey: ["petty-cash", selectedBookId],
    queryFn: () =>
      api<PettyCashTx[]>(`/api/v1/books/${selectedBookId}/petty-cash/transactions`),
    enabled: selectedBookId !== null,
  });

  const balanceQuery = useQuery({
    queryKey: ["petty-balance", selectedBookId],
    queryFn: () =>
      api<{ bookId: number; balanceMinor: number }>(
        `/api/v1/books/${selectedBookId}/petty-cash/balance`,
      ),
    enabled: selectedBookId !== null,
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["petty-cash", selectedBookId] });
    queryClient.invalidateQueries({ queryKey: ["petty-balance", selectedBookId] });
    queryClient.invalidateQueries({ queryKey: ["cash-days", selectedBookId] });
  };

  const saveMutation = useMutation({
    mutationFn: (input: PettyCashInput) =>
      editingId
        ? api<PettyCashTx>(
            `/api/v1/books/${selectedBookId}/petty-cash/transactions/${editingId}`,
            { method: "PUT", body: JSON.stringify(input) },
          )
        : api<PettyCashTx>(
            `/api/v1/books/${selectedBookId}/petty-cash/transactions`,
            { method: "POST", body: JSON.stringify(input) },
          ),
    onSuccess: (saved) => {
      invalidate();
      setLinked(
        saved.type === "PUT" && saved.linkedCashDayId !== null
          ? `Linked: AED ${filsToAed(saved.amountMinor)} added to the cash sheet withdrawal on ${fmtDate(saved.date)}`
          : saved.type === "PUT"
            ? "Top-up recorded (no cash day row on that date yet — it will appear when you enter the day)"
            : null,
      );
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
      api<void>(`/api/v1/books/${selectedBookId}/petty-cash/transactions/${id}`, {
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
    setLinked(null);

    const amount = aedToFils(form.amount);
    if (amount === null || amount <= 0) {
      setError("Enter an amount greater than 0");
      return;
    }

    saveMutation.mutate({
      date: form.date,
      description: form.description.trim(),
      type,
      amountMinor: amount,
    });
  }

  function startEdit(tx: PettyCashTx) {
    setEditingId(tx.id);
    setType(tx.type);
    setLinked(null);
    setError(null);
    setForm({
      date: tx.date,
      description: tx.description,
      amount: filsToAed(tx.amountMinor),
    });
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function cancelEdit() {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setLinked(null);
    setError(null);
  }

  if (isLoading || selectedBookId === null) return <Spinner />;

  const currency = selectedBook?.currencyCode ?? "AED";
  const balance = balanceQuery.data?.balanceMinor ?? 0;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-stone-900">Petty cash</h1>
        <p className="text-sm text-stone-500">
          {selectedBook?.name} · balance = all top-ups − all takes
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <StatCard
          label="Petty cash balance"
          value={filsToAedWithCurrency(balance, currency)}
          tone={balance < 0 ? "red" : balance > 0 ? "green" : "default"}
        />
        <StatCard
          label="Transactions"
          value={String(txs.length)}
          sub="Top-ups link to the cash sheet automatically"
        />
      </div>

      {isEditor ? (
        <Card
          title={
            editingId
              ? `Editing ${fmtDate(form.date)}`
              : "New transaction"
          }
        >
          <form onSubmit={onSubmit} className="space-y-4">
            <div className="flex flex-wrap items-end gap-3">
              <div className="flex overflow-hidden rounded-lg border border-stone-300">
                <button
                  type="button"
                  onClick={() => setType("PUT")}
                  className={`px-4 py-2 text-sm font-medium transition ${
                    type === "PUT"
                      ? "bg-emerald-600 text-white"
                      : "bg-white text-stone-600 hover:bg-stone-50"
                  }`}
                >
                  Top-up (into petty cash)
                </button>
                <button
                  type="button"
                  onClick={() => setType("TAKE")}
                  className={`px-4 py-2 text-sm font-medium transition ${
                    type === "TAKE"
                      ? "bg-red-600 text-white"
                      : "bg-white text-stone-600 hover:bg-stone-50"
                  }`}
                >
                  Take (spent)
                </button>
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
              <div className="flex-1 basis-52">
                <Field label="Description">
                  <Input
                    value={form.description}
                    onChange={(e) =>
                      setForm((f) => ({ ...f, description: e.target.value }))
                    }
                    placeholder={
                      type === "PUT" ? "e.g. Petty cash top-up" : "e.g. Office supplies"
                    }
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
            </div>

            {type === "PUT" ? (
              <p className="flex items-start gap-1.5 text-xs text-stone-500">
                <Info className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
                <span>
                  A top-up automatically adds this amount to the{" "}
                  <strong>cash sheet withdrawal</strong> for the same date — one
                  entry, both ledgers.
                </span>
              </p>
            ) : null}

            {error ? <ErrorBanner message={error} /> : null}
            {linked ? (
              <div className="flex items-start gap-2 rounded-lg border border-emerald-300 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
                <CircleCheck className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
                <span>{linked}</span>
              </div>
            ) : null}

            <div className="flex gap-2">
              <Button type="submit" disabled={saveMutation.isPending}>
                {saveMutation.isPending
                  ? "Saving…"
                  : editingId
                    ? "Save changes"
                    : type === "PUT"
                      ? "Add top-up"
                      : "Add take"}
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

      <Card title="Ledger">
        {txs.length === 0 ? (
          <EmptyState>
            No transactions yet{isEditor ? " — record the first top-up or take above" : ""}.
          </EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[560px] border-collapse">
              <thead className="border-b border-stone-200">
                <tr>
                  <Th>Date</Th>
                  <Th>Description</Th>
                  <Th>Type</Th>
                  <Th>Amount</Th>
                  <Th>Balance</Th>
                  {isEditor ? <Th /> : null}
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {txs.map((tx) => (
                  <tr key={tx.id} className="hover:bg-stone-50">
                    <Td className="font-medium text-stone-900">{fmtDate(tx.date)}</Td>
                    <Td className="whitespace-normal">{tx.description}</Td>
                    <Td>
                      <Badge tone={tx.type === "PUT" ? "green" : "red"}>
                        {tx.type === "PUT" ? "Top-up" : "Take"}
                      </Badge>
                    </Td>
                    <Td
                      className={
                        tx.type === "PUT" ? "text-emerald-700" : "text-red-600"
                      }
                    >
                      {tx.type === "PUT" ? "+" : "−"}
                      {filsToAed(tx.amountMinor)}
                    </Td>
                    <Td className="font-semibold">{filsToAed(tx.balanceMinor)}</Td>
                    {isEditor ? (
                      <Td>
                        <div className="flex justify-end gap-1">
                          <Button
                            variant="ghost"
                            className="!px-2 !py-1"
                            onClick={() => startEdit(tx)}
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
                                  `Delete this ${tx.type === "PUT" ? "top-up" : "take"} (${filsToAed(tx.amountMinor)} AED)?`,
                                )
                              ) {
                                deleteMutation.mutate(tx.id);
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
