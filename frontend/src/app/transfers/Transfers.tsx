"use client";

import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
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
  const { t } = useTranslation();
  const { books } = useBook();
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
          ? t("transfers.linkedMsg", {
              amount: filsToAed(saved.amountMinor),
              extra:
                saved.linkedCashDayExtraMinor !== null
                  ? t("transfers.linkedExtra", {
                      extra: filsToAed(saved.linkedCashDayExtraMinor),
                    })
                  : "",
            })
          : null,
      );
      setForm({ fromBookId: "", toBookId: "", date: todayISO(), amount: "", ref: "" });
      setLinkParking(false);
      setEditingId(null);
      setError(null);
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : t("common.saveFailed")),
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
      setError(t("common.enterAmountGreaterThanZero"));
      return;
    }
    if (!form.fromBookId || !form.toBookId) {
      setError(t("transfers.chooseBothBooks"));
      return;
    }
    if (form.fromBookId === form.toBookId) {
      setError(t("transfers.booksMustDiffer"));
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
        <h1 className="text-2xl font-bold text-stone-900">{t("transfers.title")}</h1>
        <p className="text-sm text-stone-500">{t("transfers.subtitle")}</p>
      </div>

      {error ? <ErrorBanner message={error} /> : null}
      {linkedMsg ? (
        <div className="flex items-start gap-2 rounded-lg border border-emerald-300 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
          <CircleCheck className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          <span>{linkedMsg}</span>
        </div>
      ) : null}

      {isEditor ? (
        <Card title={editingId ? t("transfers.editTitle") : t("transfers.newTitle")}>
          <form onSubmit={onSubmit} className="space-y-4">
            <div className="flex flex-wrap items-end gap-3">
              <div className="w-40">
                <Field label={t("transfers.fromBook")}>
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
                <Field label={t("transfers.toBook")}>
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
                <Field label={t("common.date")}>
                  <Input
                    type="date"
                    value={form.date}
                    onChange={(e) => setForm((f) => ({ ...f, date: e.target.value }))}
                    required
                  />
                </Field>
              </div>
              <div className="w-40">
                <Field label={t("common.amount")}>
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
                <Field label={t("common.ref")}>
                  <Input
                    value={form.ref}
                    onChange={(e) => setForm((f) => ({ ...f, ref: e.target.value }))}
                    placeholder={t("common.optional")}
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
                {t("transfers.parkingLinkCheckbox")}
              </label>
            ) : null}

            <div className="flex gap-2">
              <Button type="submit" disabled={saveMutation.isPending}>
                {saveMutation.isPending ? t("common.saving") : editingId ? t("common.saveChanges") : t("transfers.add")}
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
                  {t("common.cancel")}
                </Button>
              ) : null}
            </div>
          </form>
        </Card>
      ) : null}

      <Card title={t("transfers.title")}>
        <div className="mb-3 w-44">
          <Field label={t("transfers.bookFilter")}>
            <Select value={bookFilter} onChange={(e) => setBookFilter(e.target.value)}>
              <option value="">{t("transfers.allBooks")}</option>
              {books.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name}
                </option>
              ))}
            </Select>
          </Field>
        </div>
        {transfers.length === 0 ? (
          <EmptyState>{t("transfers.noTransfers")}</EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[680px] border-collapse">
              <thead className="border-b border-stone-200">
                <tr>
                  <Th>{t("common.date")}</Th>
                  <Th>{t("transfers.fromTo")}</Th>
                  <Th>{t("common.amount")}</Th>
                  <Th>{t("common.ref")}</Th>
                  <Th>{t("transfers.linkage")}</Th>
                  {isEditor ? <Th /> : null}
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {transfers.map((tr) => (
                  <tr key={tr.id} className="hover:bg-stone-50">
                    <Td className="font-medium text-stone-900">{fmtDate(tr.date)}</Td>
                    <Td>
                      {bookName(tr.fromBookId)} → {bookName(tr.toBookId)}
                    </Td>
                    <Td className="font-semibold">
                      {filsToAedWithCurrency(tr.amountMinor, tr.currencyCode)}
                    </Td>
                    <Td>{tr.ref ?? "—"}</Td>
                    <Td>
                      {tr.linkedParkingMove ? (
                        <Badge tone="amber">
                          {t("transfers.parkingShop")}
                          {tr.linkedCashDayExtraMinor !== null
                            ? t("transfers.badgeExtra", { extra: filsToAed(tr.linkedCashDayExtraMinor) })
                            : ""}
                        </Badge>
                      ) : (
                        <span className="text-xs text-stone-400">—</span>
                      )}
                    </Td>
                    {isEditor ? (
                      <Td>
                        <div className="flex justify-end gap-1">
                          <Button variant="ghost" className="!px-2 !py-1" onClick={() => startEdit(tr)}>
                            {t("common.edit")}
                          </Button>
                          <Button
                            variant="ghost"
                            className="!px-2 !py-1"
                            disabled={deleteMutation.isPending}
                            onClick={() => {
                              if (confirm(t("transfers.deleteConfirm", { amount: filsToAed(tr.amountMinor) }))) {
                                deleteMutation.mutate(tr.id);
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
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
