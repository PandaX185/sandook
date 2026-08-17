"use client";

import { Fragment, useState, type FormEvent } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";

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
  ParkingBooking,
  ParkingBookingInput,
  ParkingBookingInterval,
  ParkingBookingPayInput,
  ParkingBookingStatus,
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
  Td,
  Th,
} from "@/components/ui";

const EMPTY_BOOKING = {
  plateNo: "",
  rate: "",
  intervalType: "MONTHLY" as ParkingBookingInterval,
  customMonths: "",
  nextDueDate: todayISO(),
};

export function BookingsTab({
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
              <div className="w-full sm:w-44">
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
              <div className="w-full sm:w-40">
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
                <div className="w-full sm:w-28">
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
              <div className="w-full sm:w-44">
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
              <div className="col-span-2 flex gap-2 sm:col-span-auto">
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
          <>
            <div className="space-y-3 md:hidden">
              {bookings.map((booking) => (
                <div key={booking.id} className="rounded-lg border border-stone-200 p-3">
                  <div className="mb-2 flex items-center justify-between">
                    <span className="font-semibold text-stone-900">{booking.plateNo}</span>
                    {statusBadge(booking.status)}
                  </div>
                  <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
                    <span className="text-stone-500">{t("parking.monthlyRate")}</span>
                    <span className="text-right">{filsToAedWithCurrency(booking.monthlyRateMinor, currency)}</span>
                    <span className="text-stone-500">{t("parking.interval")}</span>
                    <span className="text-right">{intervalLabel(booking.intervalType, booking.intervalMonths)}</span>
                    <span className="text-stone-500">{t("parking.nextDueDate")}</span>
                    <span className="text-right">
                      {fmtDate(booking.nextDueDate)}
                      {booking.paidThroughDate ? (
                        <span className="block text-stone-400">{t("parking.paidThru")} {fmtDate(booking.paidThroughDate)}</span>
                      ) : null}
                    </span>
                  </div>
                  {isEditor ? (
                    <div className="mt-2 flex flex-wrap gap-1.5 border-t border-stone-100 pt-2">
                      <Button variant="ghost" className="!px-2 !py-1 !text-xs" onClick={() => openPay(booking)}>
                        {t("parking.pay")}
                      </Button>
                      <Button variant="ghost" className="!px-2 !py-1 !text-xs" onClick={() => startEdit(booking)}>
                        {t("common.edit")}
                      </Button>
                      <Button
                        variant="ghost"
                        className="!px-2 !py-1 !text-xs"
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
                  ) : null}
                </div>
              ))}
            </div>
            <div className="overflow-x-auto max-md:hidden">
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
          </>
        )}
      </Card>
    </div>
  );
}
