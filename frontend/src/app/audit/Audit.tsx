"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { filsToAedWithCurrency, fmtDate } from "@/lib/format";
import type { AuditAction, AuditEntry } from "@/lib/types";
import { Badge, Card, EmptyState, Field, Select, Spinner } from "@/components/ui";

const ENTITIES = [
  "cash_day",
  "petty_cash_tx",
  "parking_bill",
  "parking_cash_move",
  "parking_booking",
  "transfer",
  "user",
];

const ENTITY_LABELS: Record<string, string> = {
  cash_day: "Cash day",
  petty_cash_tx: "Petty cash",
  parking_bill: "Parking bill",
  parking_cash_move: "Parking cash move",
  parking_booking: "Parking booking",
  transfer: "Transfer",
  user: "User",
};

const ACTION_TONE: Record<AuditAction, "green" | "amber" | "red"> = {
  CREATE: "green",
  UPDATE: "amber",
  DELETE: "red",
};

const COMMON_LABELS: Record<string, string> = {
  id: "ID",
  bookId: "Book",
  date: "Date",
  type: "Type",
  amountMinor: "Amount",
  balanceMinor: "Balance",
  description: "Description",
  enteredBy: "Entered by",
  createdAt: "Created at",
  currencyCode: "Currency",
  ref: "Reference",
};

const FIELD_LABELS: Record<string, Record<string, string>> = {
  cash_day: {
    salesMinor: "Sales",
    extraMinor: "Extra income",
    withdrawMinor: "Withdrawals",
    depositMinor: "Deposits",
    netCashMinor: "Net cash",
    depositRemarks: "Deposit remarks",
    notes: "Notes",
  },
  petty_cash_tx: {
    linkedCashDayId: "Linked cash day",
    linkedCashDayWithdrawMinor: "Linked withdrawal",
  },
  parking_bill: {
    plateNo: "Plate no.",
    paymentMethod: "Payment",
    billedAt: "Billed at",
  },
  parking_cash_move: {
    salaryPayments: "Salary payments",
  },
  parking_booking: {
    plateNo: "Plate no.",
    monthlyRateMinor: "Monthly rate",
    renewalMonth: "Renewal month",
    active: "Active",
    due: "Due",
  },
  transfer: {
    fromBookId: "From book",
    toBookId: "To book",
    linkedParkingMove: "Linked parking move",
    linkedMoveId: "Linked move",
    linkedCashDayId: "Linked cash day",
    linkedCashDayExtraMinor: "Linked extra",
  },
  user: {
    username: "Username",
    role: "Role",
    active: "Active",
  },
};

const ENUM_LABELS: Record<string, Record<string, string>> = {
  petty_cash_tx: { PUT: "Top-up", TAKE: "Take" },
  parking_cash_move: {
    OPENING: "Opening",
    TRANSFER_TO_SHOP: "Transfer to shop",
    SALARY: "Salary",
    EXPENSE: "Expense",
    CLOSING: "Closing",
  },
  parking_bill: { CASH: "Cash", CARD: "Card" },
  user: { EDITOR: "Editor", VIEWER: "Viewer" },
};

const MINOR_KEYS = new Set([
  "amountMinor",
  "balanceMinor",
  "salesMinor",
  "extraMinor",
  "withdrawMinor",
  "depositMinor",
  "netCashMinor",
  "monthlyRateMinor",
  "linkedCashDayWithdrawMinor",
  "linkedCashDayExtraMinor",
]);

function fmtValue(v: unknown): string {
  if (v === undefined) return "—";
  if (v === null) return "null";
  if (typeof v === "string") return v;
  if (typeof v === "object") return JSON.stringify(v);
  return String(v);
}

function fieldLabel(entity: string, key: string): string {
  return FIELD_LABELS[entity]?.[key] ?? COMMON_LABELS[key] ?? key;
}

function fmtFieldValue(entity: string, key: string, v: unknown): string {
  if (v === null || v === undefined) return fmtValue(v);
  if (typeof v === "number") {
    return MINOR_KEYS.has(key) ? filsToAedWithCurrency(v) : String(v);
  }
  if (typeof v === "boolean") return v ? "Yes" : "No";
  if (typeof v === "string") {
    const enumLabel = ENUM_LABELS[entity]?.[v];
    if (enumLabel) return enumLabel;
    if (/^\d{4}-\d{2}-\d{2}$/.test(v)) return fmtDate(v);
  }
  return fmtValue(v);
}

function diffOf(oldValue: unknown, newValue: unknown) {
  const oldObj = (oldValue ?? {}) as Record<string, unknown>;
  const newObj = (newValue ?? {}) as Record<string, unknown>;
  const keys = Array.from(new Set([...Object.keys(oldObj), ...Object.keys(newObj)]));
  return keys
    .filter((k) => JSON.stringify(oldObj[k]) !== JSON.stringify(newObj[k]))
    .map((k) => ({ key: k, old: oldObj[k], next: newObj[k] }));
}

export function Audit() {
  const [entity, setEntity] = useState("");
  const [action, setAction] = useState("");
  const [limit, setLimit] = useState(100);

  const { data: entries = [], isLoading } = useQuery({
    queryKey: ["audit", entity, action, limit],
    queryFn: () => {
      const params = new URLSearchParams();
      if (entity) params.set("entity", entity);
      if (action) params.set("action", action);
      params.set("limit", String(limit));
      return api<AuditEntry[]>(`/api/v1/audit?${params.toString()}`);
    },
  });

  if (isLoading) return <Spinner />;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-stone-900">Audit log</h1>
        <p className="text-sm text-stone-500">
          Every write to the ledgers — who did what, when, and only the fields
          that changed
        </p>
      </div>

      <Card title="Filters">
        <div className="flex flex-wrap items-end gap-3">
          <div className="w-52">
            <Field label="Entity">
              <Select value={entity} onChange={(e) => setEntity(e.target.value)}>
                <option value="">All entities</option>
                {ENTITIES.map((e) => (
                  <option key={e} value={e}>
                    {ENTITY_LABELS[e] ?? e}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <div className="w-40">
            <Field label="Action">
              <Select value={action} onChange={(e) => setAction(e.target.value)}>
                <option value="">All actions</option>
                <option value="CREATE">CREATE</option>
                <option value="UPDATE">UPDATE</option>
                <option value="DELETE">DELETE</option>
              </Select>
            </Field>
          </div>
          <div className="w-32">
            <Field label="Limit">
              <Select value={String(limit)} onChange={(e) => setLimit(Number(e.target.value))}>
                <option value="25">25</option>
                <option value="50">50</option>
                <option value="100">100</option>
                <option value="250">250</option>
                <option value="500">500</option>
              </Select>
            </Field>
          </div>
        </div>
      </Card>

      <Card title={`Entries (${entries.length})`}>
        {entries.length === 0 ? (
          <EmptyState>No audit entries match these filters.</EmptyState>
        ) : (
          <div className="space-y-3">
            {entries.map((entry) => (
              <div key={entry.id} className="rounded-lg border border-stone-200 p-3">
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                  <Badge tone={ACTION_TONE[entry.action]}>{entry.action}</Badge>
                  <span className="text-sm font-semibold text-stone-800">
                    {ENTITY_LABELS[entry.entity] ?? entry.entity}
                  </span>
                  <span className="text-xs text-stone-400">id {entry.entityId}</span>
                  <span className="ml-auto text-xs text-stone-500">
                    {entry.username ?? "system"}
                  </span>
                  <span className="text-xs text-stone-400">
                    {fmtDate(entry.createdAt.slice(0, 10))} {entry.createdAt.slice(11, 19)}
                  </span>
                </div>
                <div className="mt-2">
                  {diffOf(entry.oldValue, entry.newValue).length === 0 ? (
                    <p className="text-xs text-stone-400">No field changes</p>
                  ) : (
                    <ul className="space-y-1">
                      {diffOf(entry.oldValue, entry.newValue).map((d) => (
                        <li
                          key={d.key}
                          className="flex flex-wrap items-baseline gap-x-2 text-xs"
                        >
                          <span className="font-medium text-stone-600">
                            {fieldLabel(entry.entity, d.key)}
                          </span>
                          {d.old !== undefined ? (
                            <span className="text-stone-400 line-through">
                              {fmtFieldValue(entry.entity, d.key, d.old)}
                            </span>
                          ) : null}
                          {d.old !== undefined && d.next !== undefined ? (
                            <span className="text-stone-400">→</span>
                          ) : null}
                          {d.next !== undefined ? (
                            <span className="text-emerald-700">
                              {fmtFieldValue(entry.entity, d.key, d.next)}
                            </span>
                          ) : null}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}
