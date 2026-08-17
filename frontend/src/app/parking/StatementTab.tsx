"use client";

import { useEffect, useState, type FormEvent } from "react";
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
  ParkingCashMove,
  ParkingCashMoveInput,
  ParkingCashMoveType,
  ParkingStatement,
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

const NOTE_CHIP_KEYS: { key: string; value: string }[] = [
  { key: "parking.salary", value: "Salary" },
  { key: "parking.maintenance", value: "Maintenance" },
  { key: "parking.utilities", value: "Utilities" },
  { key: "parking.cleaning", value: "Cleaning" },
  { key: "parking.rent", value: "Rent" },
  { key: "parking.other", value: "Other" },
];

const EMPTY_MOVE = { date: todayISO(), type: "EXPENSE" as ParkingCashMoveType, amount: "", description: "" };

export function StatementTab({
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
      <div className="grid grid-cols-2 gap-3 sm:flex sm:flex-wrap sm:items-end">
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
                    {t("parking.sum")} {filsToAed(salaryRows.reduce((s, r) => s + (aedToFils(r.amount) ?? 0), 0))} {currency}
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
          <>
            <div className="space-y-3 md:hidden">
              {statement.days.map((day, idx) => (
                <div key={day.date} className={`rounded-lg border border-stone-200 p-3 ${idx % 2 === 0 ? "bg-white" : "bg-stone-50/50"}`}>
                  <div className="mb-2 flex items-center justify-between">
                    <span className="font-semibold text-stone-900">{fmtDate(day.date)}</span>
                    <span className="font-semibold text-stone-900">{filsToAed(day.cumulativeMinor)}</span>
                  </div>
                  <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
                    <span className="text-stone-500">{t("parking.opening")}</span>
                    <span className="text-right">{filsToAed(day.openingMinor)}</span>
                    <span className="text-stone-500">{t("payment.CASH")}</span>
                    <span className="text-right text-emerald-700">+{filsToAed(day.cashBillsMinor)}</span>
                    <span className="text-stone-500">{t("payment.CARD")}</span>
                    <span className="text-right text-emerald-700">+{filsToAed(day.cardBillsMinor)}</span>
                    <span className="text-stone-500">{t("parking.bookings2")}</span>
                    <span className="text-right text-emerald-700">+{filsToAed(day.bookingsMinor)}</span>
                    <span className="text-stone-500">{t("parking.toShop")}</span>
                    <span className="text-right text-amber-700">−{filsToAed(day.transfersToShopMinor)}</span>
                    <span className="text-stone-500">{t("parking.salaries")}</span>
                    <span className="text-right text-red-600">−{filsToAed(day.salariesMinor)}</span>
                    <span className="text-stone-500">{t("parking.expenses")}</span>
                    <span className="text-right text-red-600">
                      −{filsToAed(day.expensesMinor)}
                      {day.expenseNotes.length > 0 ? (
                        <span className="block font-normal text-stone-400">{day.expenseNotes.join(" · ")}</span>
                      ) : null}
                    </span>
                    <span className="text-stone-500">{t("parking.netOut")}</span>
                    <span className="text-right">−{filsToAed(day.netOutMinor)}</span>
                    <span className="text-stone-500">{t("parking.closing")}</span>
                    <span className="text-right font-semibold">{filsToAed(day.closingMinor)}</span>
                  </div>
                </div>
              ))}
            </div>
            <div className="overflow-x-auto max-md:hidden">
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
          </>
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
