"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
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

const ENTITY_KEYS: Record<string, string> = {
  cash_day: "audit.entities.cash_day",
  petty_cash_tx: "audit.entities.petty_cash_tx",
  parking_bill: "audit.entities.parking_bill",
  parking_cash_move: "audit.entities.parking_cash_move",
  parking_booking: "audit.entities.parking_booking",
  transfer: "audit.entities.transfer",
  user: "audit.entities.user",
};

const ACTION_TONE: Record<AuditAction, "green" | "amber" | "red"> = {
  CREATE: "green",
  UPDATE: "amber",
  DELETE: "red",
};

const COMMON_KEYS: Record<string, string> = {
  id: "audit.fields.id",
  bookId: "audit.fields.bookId",
  date: "audit.fields.date",
  type: "audit.fields.type",
  amountMinor: "audit.fields.amountMinor",
  balanceMinor: "audit.fields.balanceMinor",
  description: "audit.fields.description",
  enteredBy: "audit.fields.enteredBy",
  createdAt: "audit.fields.createdAt",
  currencyCode: "audit.fields.currencyCode",
  ref: "audit.fields.ref",
};

const FIELD_KEYS: Record<string, Record<string, string>> = {
  cash_day: {
    salesMinor: "audit.fields.salesMinor",
    extraMinor: "audit.fields.extraMinor",
    withdrawMinor: "audit.fields.withdrawMinor",
    depositMinor: "audit.fields.depositMinor",
    netCashMinor: "audit.fields.netCashMinor",
    depositRemarks: "audit.fields.depositRemarks",
    notes: "audit.fields.notes",
  },
  petty_cash_tx: {
    linkedCashDayId: "audit.fields.linkedCashDayId",
    linkedCashDayWithdrawMinor: "audit.fields.linkedCashDayWithdrawMinor",
  },
  parking_bill: {
    plateNo: "audit.fields.plateNo",
    paymentMethod: "audit.fields.paymentMethod",
    billedAt: "audit.fields.billedAt",
  },
  parking_cash_move: {
    salaryPayments: "audit.fields.salaryPayments",
  },
  parking_booking: {
    plateNo: "audit.fields.plateNo",
    monthlyRateMinor: "audit.fields.monthlyRateMinor",
    intervalType: "audit.fields.intervalType",
    intervalMonths: "audit.fields.intervalMonths",
    nextDueDate: "audit.fields.nextDueDate",
    paidThroughDate: "audit.fields.paidThroughDate",
    active: "audit.fields.active",
    status: "audit.fields.status",
  },
  transfer: {
    fromBookId: "audit.fields.fromBookId",
    toBookId: "audit.fields.toBookId",
    linkedParkingMove: "audit.fields.linkedParkingMove",
    linkedMoveId: "audit.fields.linkedMoveId",
    linkedCashDayId: "audit.fields.linkedCashDayId",
    linkedCashDayExtraMinor: "audit.fields.linkedCashDayExtraMinor",
  },
  user: {
    username: "audit.fields.username",
    role: "audit.fields.role",
    active: "audit.fields.active",
  },
};

const ENUM_KEYS: Record<string, Record<string, string>> = {
  petty_cash_tx: { PUT: "audit.enums.PUT", TAKE: "audit.enums.TAKE" },
  parking_cash_move: {
    OPENING: "audit.enums.OPENING",
    TRANSFER_TO_SHOP: "audit.enums.TRANSFER_TO_SHOP",
    SALARY: "audit.enums.SALARY",
    EXPENSE: "audit.enums.EXPENSE",
    CLOSING: "audit.enums.CLOSING",
  },
  parking_bill: { CASH: "audit.enums.CASH", CARD: "audit.enums.CARD" },
  user: { EDITOR: "audit.enums.EDITOR", VIEWER: "audit.enums.VIEWER" },
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
  return FIELD_KEYS[entity]?.[key] ?? COMMON_KEYS[key] ?? key;
}

function fmtFieldValue(entity: string, key: string, v: unknown, t: (k: string) => string): string {
  if (v === null || v === undefined) return fmtValue(v);
  if (typeof v === "number") {
    return MINOR_KEYS.has(key) ? filsToAedWithCurrency(v) : String(v);
  }
  if (typeof v === "boolean") return v ? t("common.yes") : t("common.no");
  if (typeof v === "string") {
    const enumKey = ENUM_KEYS[entity]?.[v];
    if (enumKey) return t(enumKey);
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
  const { t } = useTranslation();
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
        <h1 className="text-2xl font-bold text-stone-900">{t("audit.title")}</h1>
        <p className="text-sm text-stone-500">{t("audit.subtitle")}</p>
      </div>

      <Card title={t("audit.filters")}>
        <div className="flex flex-wrap items-end gap-3">
          <div className="w-52">
            <Field label={t("audit.entity")}>
              <Select value={entity} onChange={(e) => setEntity(e.target.value)}>
                <option value="">{t("audit.allEntities")}</option>
                {ENTITIES.map((e) => (
                  <option key={e} value={e}>
                    {t(ENTITY_KEYS[e])}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <div className="w-40">
            <Field label={t("audit.action")}>
              <Select value={action} onChange={(e) => setAction(e.target.value)}>
                <option value="">{t("audit.allActions")}</option>
                <option value="CREATE">{t("audit.actions.CREATE")}</option>
                <option value="UPDATE">{t("audit.actions.UPDATE")}</option>
                <option value="DELETE">{t("audit.actions.DELETE")}</option>
              </Select>
            </Field>
          </div>
          <div className="w-32">
            <Field label={t("audit.limit")}>
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

      <Card title={t("audit.entriesTitle", { count: entries.length })}>
        {entries.length === 0 ? (
          <EmptyState>{t("audit.noEntries")}</EmptyState>
        ) : (
          <div className="space-y-3">
            {entries.map((entry) => (
              <div key={entry.id} className="rounded-lg border border-stone-200 p-3">
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                  <Badge tone={ACTION_TONE[entry.action]}>{t("audit.actions." + entry.action)}</Badge>
                  <span className="text-sm font-semibold text-stone-800">
                    {t(ENTITY_KEYS[entry.entity] ?? "audit.entities." + entry.entity)}
                  </span>
                  <span className="text-xs text-stone-400">id {entry.entityId}</span>
                  <span className="ms-auto text-xs text-stone-500">
                    {entry.username ?? t("audit.systemUser")}
                  </span>
                  <span className="text-xs text-stone-400">
                    {fmtDate(entry.createdAt.slice(0, 10))} {entry.createdAt.slice(11, 19)}
                  </span>
                </div>
                <div className="mt-2">
                  {diffOf(entry.oldValue, entry.newValue).length === 0 ? (
                    <p className="text-xs text-stone-400">{t("audit.noFieldChanges")}</p>
                  ) : (
                    <ul className="space-y-1">
                      {diffOf(entry.oldValue, entry.newValue).map((d) => (
                        <li
                          key={d.key}
                          className="flex flex-wrap items-baseline gap-x-2 text-xs"
                        >
                          <span className="font-medium text-stone-600">
                            {t(fieldLabel(entry.entity, d.key))}
                          </span>
                          {d.old !== undefined ? (
                            <span className="text-stone-400 line-through">
                              {fmtFieldValue(entry.entity, d.key, d.old, t)}
                            </span>
                          ) : null}
                          {d.old !== undefined && d.next !== undefined ? (
                            <span className="text-stone-400">→</span>
                          ) : null}
                          {d.next !== undefined ? (
                            <span className="text-emerald-700">
                              {fmtFieldValue(entry.entity, d.key, d.next, t)}
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
