"use client";

import { Fragment, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { api, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useBook } from "@/lib/books";
import { aedToFils, filsToAed, filsToAedInput, filsToAedWithCurrency, fmtDate, todayISO } from "@/lib/format";
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
  const { t } = useTranslation();
  const { selectedBookId, selectedBook } = useBook();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const isEditor = user?.role === "EDITOR";

  const [form, setForm] = useState(EMPTY_FORM);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [expandedId, setExpandedId] = useState<number | null>(null);
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
      setError(err instanceof ApiError ? err.message : t("common.saveFailed"));
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
      sales: filsToAedInput(day.salesMinor),
      extra: filsToAedInput(day.extraMinor),
      withdraw: filsToAedInput(day.withdrawMinor),
      deposit: filsToAedInput(day.depositMinor),
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
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-stone-900">{t("cash.title")}</h1>
          <p className="text-sm text-stone-500">
            {selectedBook?.name} · {t("cash.balanceFormula")}
          </p>
        </div>
      </div>

      {isEditor ? (
        <Card title={editingId ? t("cash.editingDay", { date: fmtDate(form.date) }) : t("cash.newDayEntry")}>
          <form onSubmit={onSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
              <Field label={t("common.date")}>
                <Input
                  type="date"
                  value={form.date}
                  onChange={(e) => set("date")(e.target.value)}
                  required
                />
              </Field>
              <Field label={t("cash.salesAed")}>
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
              <Field label={t("cash.extraAed")}>
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
              <Field label={t("cash.withdrawAed")}>
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
              <Field label={t("cash.depositAed")}>
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
              <Field label={t("cash.depositRemarks")}>
                <Input
                  value={form.depositRemarks}
                  onChange={(e) => set("depositRemarks")(e.target.value)}
                  placeholder={t("cash.bankTransferRef")}
                />
              </Field>
              <Field label={t("common.ref")}>
                <Input
                  value={form.ref}
                  onChange={(e) => set("ref")(e.target.value)}
                  placeholder={t("cash.invoiceRef")}
                />
              </Field>
              <Field label={t("common.notes")}>
                <Input
                  value={form.notes}
                  onChange={(e) => set("notes")(e.target.value)}
                  placeholder={t("cash.notesPh")}
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
                  ? t("common.saving")
                  : editingId
                    ? t("common.saveChanges")
                    : t("cash.addDay")}
              </Button>
              {editingId ? (
                <Button type="button" variant="secondary" onClick={cancelEdit}>
                  {t("common.cancel")}
                </Button>
              ) : null}
            </div>
          </form>
        </Card>
      ) : null}

      <Card title={t("cash.history")}>
        {days.length === 0 ? (
          <EmptyState>
            {isEditor ? t("cash.noEntriesEditor") : t("cash.noEntries")}
          </EmptyState>
        ) : (
          <>
            <div className="space-y-2 md:hidden">
              {days.map((day) => (
                <div key={day.id} className="rounded-lg border border-stone-200 p-3">
                  <div className="mb-1 flex items-center justify-between">
                    <span className="font-medium text-stone-900">
                      {fmtDate(day.date)}
                      {day.depositMinor > 0 ? (
                        <span className="ms-1.5"><Badge tone="amber">{t("cash.depositBadge")}</Badge></span>
                      ) : null}
                    </span>
                    <span className={`font-semibold ${day.balanceMinor < 0 ? "text-red-600" : "text-emerald-700"}`}>
                      {filsToAed(day.balanceMinor)}
                    </span>
                  </div>
                  <div className="grid grid-cols-2 gap-x-4 gap-y-0.5 text-xs">
                    <span className="text-stone-500">{t("cash.sales")}</span>
                    <span className="text-right">{filsToAed(day.salesMinor)}</span>
                    <span className="text-stone-500">{t("cash.extra")}</span>
                    <span className="text-right">{filsToAed(day.extraMinor)}</span>
                    <span className="text-stone-500">{t("cash.withdraw")}</span>
                    <span className="text-right">{filsToAed(day.withdrawMinor)}</span>
                    <span className="text-stone-500">{t("cash.deposit")}</span>
                    <span className="text-right">{filsToAed(day.depositMinor)}</span>
                    <span className="text-stone-500">{t("cash.net")}</span>
                    <span className={`text-right ${day.netCashMinor < 0 ? "text-red-600" : ""}`}>{filsToAed(day.netCashMinor)}</span>
                  </div>
                  {isEditor ? (
                    <div className="mt-2 flex gap-1.5 border-t border-stone-100 pt-2">
                      <Button variant="ghost" className="!px-2 !py-1 !text-xs" onClick={() => startEdit(day)}>
                        {t("common.edit")}
                      </Button>
                      <Button variant="ghost" className="!px-2 !py-1 !text-xs" onClick={() => {
                        if (confirm(t("cash.deleteDayConfirm", { date: fmtDate(day.date) }))) {
                          deleteMutation.mutate(day.id);
                        }
                      }}>
                        {t("common.delete")}
                      </Button>
                      <Button variant="ghost" className="!px-2 !py-1 !text-xs" onClick={() => setExpandedId(expandedId === day.id ? null : day.id)}>
                        {expandedId === day.id ? t("common.hide") : t("common.details")}
                      </Button>
                    </div>
                  ) : null}
                  {expandedId === day.id ? (
                    <div className="mt-2 grid grid-cols-2 gap-2 border-t border-stone-100 pt-2 text-xs">
                      <div><span className="text-stone-500">{t("cash.ref")}:</span> {day.ref ?? "-"}</div>
                      <div><span className="text-stone-500">{t("cash.remarks")}:</span> {day.depositRemarks ?? "-"}</div>
                      <div className="col-span-2"><span className="text-stone-500">{t("cash.notes")}:</span> {day.notes ?? "-"}</div>
                    </div>
                  ) : null}
                </div>
              ))}
            </div>
            <div className="overflow-x-auto max-md:hidden">
              <table className="w-full min-w-160 border-collapse">
              <thead className="border-b border-stone-200">
                <tr>
                  <Th>{t("common.date")}</Th>
                  <Th>{t("cash.sales")}</Th>
                  <Th>{t("cash.extra")}</Th>
                  <Th>{t("cash.withdraw")}</Th>
                  <Th>{t("cash.deposit")}</Th>
                  <Th>{t("cash.net")}</Th>
                  <Th>{t("common.balance")}</Th>
                  {isEditor ? <Th /> : null}
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {days.map((day) => {
                  const isExpanded = expandedId === day.id;

                  return (
                    <Fragment key={day.id}>
                      <tr className="hover:bg-stone-50">
                        <Td className="font-medium text-stone-900">
                          {fmtDate(day.date)}
                          {day.depositMinor > 0 ? (
                            <span className="ml-1.5">
                              <Badge tone="amber">{t("cash.depositBadge")}</Badge>
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
                          className={`font-semibold ${day.balanceMinor < 0
                            ? "text-red-600"
                            : "text-emerald-700"
                            }`}
                        >
                          {filsToAed(day.balanceMinor)}
                        </Td>

                        {isEditor ? (
                          <Td>
                            <div className="flex justify-end gap-1">
                              <Button
                                variant="ghost"
                                className="px-2! py-1!"
                                onClick={() => startEdit(day)}
                              >
                                {t("common.edit")}
                              </Button>

                              <Button
                                variant="ghost"
                                className="px-2! py-1!"
                                onClick={() => {
                                  if (
                                    confirm(
                                      t("cash.deleteDayConfirm", {
                                        date: fmtDate(day.date),
                                      }),
                                    )
                                  ) {
                                    deleteMutation.mutate(day.id);
                                  }
                                }}
                              >
                                {t("common.delete")}
                              </Button>

                              <Button
                                variant="ghost"
                                className="px-2! py-1!"
                                onClick={() =>
                                  setExpandedId(isExpanded ? null : day.id)
                                }
                              >
                                {isExpanded ? t("common.hide") : t("common.details")}
                              </Button>
                            </div>
                          </Td>
                        ) : null}
                      </tr>

                      {isExpanded ? (
                        <tr className="bg-stone-50">
                          <Td colSpan={8} className="px-4 py-3">
                            <div className="grid gap-4 py-2 sm:grid-cols-3">
                              <div>
                                <p className="text-xs font-medium uppercase text-stone-500">
                                  {t("cash.ref")}
                                </p>
                                <p className="mt-1 text-sm text-stone-900">
                                  {day.ref ?? "-"}
                                </p>
                              </div>

                              <div>
                                <p className="text-xs font-medium uppercase text-stone-500">
                                  {t("cash.remarks")}
                                </p>
                                <p className="mt-1 text-sm text-stone-900">
                                  {day.depositRemarks ?? "-"}
                                </p>
                              </div>

                              <div>
                                <p className="text-xs font-medium uppercase text-stone-500">
                                  {t("cash.notes")}
                                </p>
                                <p className="mt-1 text-sm text-stone-900">
                                  {day.notes ?? "-"}
                                </p>
                              </div>
                            </div>
                          </Td>
                        </tr>
                      ) : null}
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
            </div>
          </>
        )}
        {days.length > 0 ? (
          <p className="mt-3 text-right text-sm font-semibold text-stone-700">
            {t("cash.balanceLabel")} {filsToAedWithCurrency(days.map((d) => d.balanceMinor).reduce((a, b) => a + b, 0), currency)}
          </p>
        ) : null}
      </Card>
    </div>
  );
}
