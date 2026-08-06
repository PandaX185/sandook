"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { fmtDate } from "@/lib/format";
import type { AuditAction, AuditEntry } from "@/lib/types";
import { Badge, Card, EmptyState, Field, Select, Spinner, Td, Th } from "@/components/ui";

const ENTITIES = [
  "cash_day",
  "petty_cash_tx",
  "parking_bill",
  "parking_cash_move",
  "parking_booking",
  "transfer",
];

const ACTION_TONE: Record<AuditAction, "green" | "amber" | "red"> = {
  CREATE: "green",
  UPDATE: "amber",
  DELETE: "red",
};

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
          Every write to the ledgers — who did what, when, and the before/after values
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
                    {e}
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
                  <span className="text-sm font-semibold text-stone-800">{entry.entity}</span>
                  <span className="text-xs text-stone-400">id {entry.entityId}</span>
                  <span className="ml-auto text-xs text-stone-500">
                    {entry.username ?? "system"}
                  </span>
                  <span className="text-xs text-stone-400">
                    {fmtDate(entry.createdAt.slice(0, 10))} {entry.createdAt.slice(11, 19)}
                  </span>
                </div>
                <div className="mt-2 grid gap-2 sm:grid-cols-2">
                  <details className="rounded-md bg-stone-50 p-2">
                    <summary className="cursor-pointer text-xs font-medium text-stone-500">
                      Old value {entry.oldValue === null ? "(null)" : ""}
                    </summary>
                    {entry.oldValue !== null ? (
                      <pre className="mt-1 overflow-x-auto text-[11px] leading-relaxed text-stone-600">
                        {JSON.stringify(entry.oldValue, null, 2)}
                      </pre>
                    ) : null}
                  </details>
                  <details className="rounded-md bg-stone-50 p-2">
                    <summary className="cursor-pointer text-xs font-medium text-stone-500">
                      New value {entry.newValue === null ? "(null)" : ""}
                    </summary>
                    {entry.newValue !== null ? (
                      <pre className="mt-1 overflow-x-auto text-[11px] leading-relaxed text-stone-600">
                        {JSON.stringify(entry.newValue, null, 2)}
                      </pre>
                    ) : null}
                  </details>
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}
