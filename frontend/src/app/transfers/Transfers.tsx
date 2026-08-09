"use client";

import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CircleCheck } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useBook } from "@/lib/books";
import { aedToFils, filsToAed, filsToAedWithCurrency, fmtDate, todayISO } from "@/lib/format";
import type { Transfer, TransferInput, TransferUpdateInput } from "@/lib/types";
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
  Td,
  Th,
} from "@/components/ui";

export function Transfers() {
  const { books, selectedBookId } = useBook();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const isEditor = user?.role === "EDITOR";

  const [error, setError] = useState<string | null>(null);
  const [linkedMsg, setLinkedMsg] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [bookFilter, setBookFilter] = useState("");

  const [form, setForm] = useState({
    fromBookId: "",
    toBookId: "",
    date: todayISO(),
    amount: "",
    ref: "",
  });
  const [linkParking, setLinkParking] = useState(false);

  const transfersQuery = useQuery({
    queryKey: ["transfers", bookFilter],
    queryFn: () => {
      const qs = bookFilter ? `?bookId=${bookFilter}` : "";
      return api<Transfer[]>(`/api/v1/transfers${qs}`);
    },
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["transfers", bookFilter] });
    queryClient.invalidateQueries({ queryKey: ["parking-statement"] });
    queryClient.invalidateQueries({ queryKey: ["cash-days"] });
  };

  const saveMutation = useMutation({
    mutationFn: (input: TransferInput | TransferUpdateInput) => {
      const body = editingId
        ? JSON.stringify(input as TransferUpdateInput)
        : JSON.stringify(input as TransferInput);
      return editingId
        ? api<Transfer>(`/api/v1/transfers/${editingId}`, { method: "PUT", body })
        : api<Transfer>("/api/v1/transfers", { method: "POST", body });
    },
    onSuccess: (saved) => {
      invalidate();
      setLinkedMsg(
        saved.linkedParkingMove
          ? `Linked: AED ${filsToAed(saved.amountMinor)} recorded as a parking → shop cash move${
              saved.linkedCashDayExtraMinor !== null
                ? ` and added to the shop cash sheet (extra AED ${filsToAed(saved.linkedCashDayExtraMinor)})`
                : ""
            }.`
          : null,
      );
      setForm({ fromBookId: "", toBookId: "", date: todayISO(), amount: "", ref: "" });
      setLinkParking(false);
      setEditingId(null);
      setError(null);
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : "Save failed"),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api<void>(`/api/v1/transfers/${id}`, { method: "DELETE" }),
    onSuccess: () => {
      invalidate();
      if (editingId !== null) {
        setEditingId(null);
        setForm({ fromBookId: "", toBookId: "", date: todayISO(), amount: "", ref: "" });
        setLinkParking(false);
      }
    },
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLinkedMsg(null);

    const amount = aedToFils(form.amount);
    if (amount === null || amount <= 0) {
      setError("Enter an amount greater than 0");
      return;
    }
    if (!form.fromBookId || !form.toBookId) {
      setError("Choose both books");
      return;
    }
    if (form.fromBookId === form.toBookId) {
      setError("From and to books must be different");
      return;
    }

    if (editingId) {
      saveMutation.mutate({
        date: form.date,
        amountMinor: amount,
        ref: form.ref.trim() || null,
      });
    } else {
      saveMutation.mutate({
        fromBookId: Number(form.fromBookId),
        toBookId: Number(form.toBookId),
        date: form.date,
        amountMinor: amount,
        ref: form.ref.trim() || null,
        linkParkingMove: linkParking,
      });
    }
  }

  function startEdit(t: Transfer) {
    setEditingId(t.id);
    setLinkedMsg(null);
    setError(null);
    setForm({
      fromBookId: String(t.fromBookId),
      toBookId: String(t.toBookId),
      date: t.date,
      amount: filsToAed(t.amountMinor),
      ref: t.ref ?? "",
    });
    setLinkParking(false);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  const transfers = transfersQuery.data ?? [];
  if (transfersQuery.isLoading) return <Spinner />;

  const bookName = (id: number) => books.find((b) => b.id === id)?.name ?? `#${id}`;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-stone-900">Transfers</h1>
        <p className="text-sm text-stone-500">Money moved between books</p>
      </div>

      {error ? <ErrorBanner message={error} /> : null}
      {linkedMsg ? (
        <div className="flex items-start gap-2 rounded-lg border border-emerald-300 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
          <CircleCheck className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          <span>{linkedMsg}</span>
        </div>
      ) : null}

      {isEditor ? (
        <Card title={editingId ? `Edit transfer (books can't change)` : "New transfer"}>
          <form onSubmit={onSubmit} className="space-y-4">
            <div className="flex flex-wrap items-end gap-3">
              <div className="w-40">
                <Field label="From book">
                  <Select
                    value={form.fromBookId}
                    disabled={editingId !== null}
                    onChange={(e) => setForm((f) => ({ ...f, fromBookId: e.target.value }))}
                  >
                    <option value="">—</option>
                    {books.map((b) => (
                      <option key={b.id} value={b.id} disabled={b.id === Number(form.toBookId)}>
                        {b.name}
                      </option>
                    ))}
                  </Select>
                </Field>
              </div>
              <div className="w-40">
                <Field label="To book">
                  <Select
                    value={form.toBookId}
                    disabled={editingId !== null}
                    onChange={(e) => setForm((f) => ({ ...f, toBookId: e.target.value }))}
                  >
                    <option value="">—</option>
                    {books.map((b) => (
                      <option key={b.id} value={b.id} disabled={b.id === Number(form.fromBookId)}>
                        {b.name}
                      </option>
                    ))}
                  </Select>
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
              <div className="w-44">
                <Field label="Ref">
                  <Input
                    value={form.ref}
                    onChange={(e) => setForm((f) => ({ ...f, ref: e.target.value }))}
                    placeholder="optional"
                  />
                </Field>
              </div>
            </div>

            {editingId === null ? (
              <label className="flex items-center gap-2 text-sm text-stone-700">
                <input
                  type="checkbox"
                  checked={linkParking}
                  onChange={(e) => setLinkParking(e.target.checked)}
                  className="h-4 w-4 rounded border-stone-300 text-emerald-600"
                />
                One-click parking → shop (records the parking cash move + shop cash sheet extra)
              </label>
            ) : null}

            <div className="flex gap-2">
              <Button type="submit" disabled={saveMutation.isPending}>
                {saveMutation.isPending ? "Saving…" : editingId ? "Save changes" : "Add transfer"}
              </Button>
              {editingId ? (
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => {
                    setEditingId(null);
                    setLinkedMsg(null);
                    setForm({ fromBookId: "", toBookId: "", date: todayISO(), amount: "", ref: "" });
                    setLinkParking(false);
                  }}
                >
                  Cancel
                </Button>
              ) : null}
            </div>
          </form>
        </Card>
      ) : null}

      <Card title="Transfers">
        <div className="mb-3 w-44">
          <Field label="Book filter">
            <Select value={bookFilter} onChange={(e) => setBookFilter(e.target.value)}>
              <option value="">All books</option>
              {books.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name}
                </option>
              ))}
            </Select>
          </Field>
        </div>
        {transfers.length === 0 ? (
          <EmptyState>No transfers yet.</EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[680px] border-collapse">
              <thead className="border-b border-stone-200">
                <tr>
                  <Th>Date</Th>
                  <Th>From → To</Th>
                  <Th>Amount</Th>
                  <Th>Ref</Th>
                  <Th>Linkage</Th>
                  {isEditor ? <Th /> : null}
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {transfers.map((t) => (
                  <tr key={t.id} className="hover:bg-stone-50">
                    <Td className="font-medium text-stone-900">{fmtDate(t.date)}</Td>
                    <Td>
                      {bookName(t.fromBookId)} → {bookName(t.toBookId)}
                    </Td>
                    <Td className="font-semibold">
                      {filsToAedWithCurrency(t.amountMinor, t.currencyCode)}
                    </Td>
                    <Td>{t.ref ?? "—"}</Td>
                    <Td>
                      {t.linkedParkingMove ? (
                        <Badge tone="amber">
                          Parking ↔ shop
                          {t.linkedCashDayExtraMinor !== null
                            ? ` · extra ${filsToAed(t.linkedCashDayExtraMinor)}`
                            : ""}
                        </Badge>
                      ) : (
                        <span className="text-xs text-stone-400">—</span>
                      )}
                    </Td>
                    {isEditor ? (
                      <Td>
                        <div className="flex justify-end gap-1">
                          <Button variant="ghost" className="!px-2 !py-1" onClick={() => startEdit(t)}>
                            Edit
                          </Button>
                          <Button
                            variant="ghost"
                            className="!px-2 !py-1"
                            disabled={deleteMutation.isPending}
                            onClick={() => {
                              if (confirm(`Delete this transfer (${filsToAed(t.amountMinor)} AED)?`)) {
                                deleteMutation.mutate(t.id);
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
