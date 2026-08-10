"use client";

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { api, ApiError } from "@/lib/api";
import type {
  ImportCommitResponse,
  ImportPreviewResponse,
  ImportPreviewRow,
} from "@/lib/types";
import { Badge, Button, ErrorBanner, Input, Spinner, Td, Th } from "@/components/ui";
import { FileUp, X } from "lucide-react";

export function ImportExcelDialog({
  bookId,
  invalidate,
  onClose,
}: {
  bookId: number;
  invalidate: () => void;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<ImportPreviewResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ImportCommitResponse | null>(null);

  const previewMutation = useMutation({
    mutationFn: (f: File) => {
      const fd = new FormData();
      fd.append("file", f);
      return api<ImportPreviewResponse>(
        `/api/v1/books/${bookId}/imports/preview`,
        { method: "POST", body: fd }
      );
    },
    onSuccess: (data) => {
      setPreview(data);
      setResult(null);
      setError(null);
    },
    onError: (err) =>
      setError(err instanceof ApiError ? err.message : t("imports.previewFailed")),
  });

  const commitMutation = useMutation({
    mutationFn: (rows: ImportPreviewRow[]) =>
      api<ImportCommitResponse>(`/api/v1/books/${bookId}/imports/commit`, {
        method: "POST",
        body: JSON.stringify({ rows }),
      }),
    onSuccess: (data) => {
      setResult(data);
      invalidate();
      setError(null);
    },
    onError: (err) =>
      setError(err instanceof ApiError ? err.message : t("imports.importFailed")),
  });

  const rows = preview?.rows ?? [];
  const validRows = rows.filter((r) => r.valid);
  const skippedSheets = preview?.skippedSheets ?? [];
  const pending = previewMutation.isPending || commitMutation.isPending;

  function onFileChange(f: File | null) {
    setFile(f);
    setPreview(null);
    setResult(null);
    setError(null);
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-stone-900/50 p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="flex max-h-[85vh] w-full max-w-3xl flex-col overflow-hidden rounded-xl bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-stone-200 px-5 py-4">
          <h2 className="text-lg font-semibold text-stone-900">{t("imports.title")}</h2>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1.5 text-stone-400 transition hover:bg-stone-100 hover:text-stone-600"
            aria-label={t("common.close")}
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="flex-1 space-y-4 overflow-y-auto px-5 py-4">
          <div className="space-y-2">
            <Input
              type="file"
              accept=".xlsx"
              onChange={(e) => onFileChange(e.target.files?.[0] ?? null)}
            />
            <Button
              type="button"
              disabled={!file || pending}
              onClick={() => {
                if (file) previewMutation.mutate(file);
              }}
            >
              <FileUp className="h-4 w-4" />
              {previewMutation.isPending
                ? t("imports.previewing")
                : preview
                  ? t("imports.rePreview")
                  : t("imports.preview")}
            </Button>
          </div>

          {error ? <ErrorBanner message={error} /> : null}

          {result ? (
            <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
              {t("imports.importedRows", {
                count: result.inserted,
                skipped: result.skipped,
              })}
            </div>
          ) : null}

          {previewMutation.isPending ? <Spinner label={t("imports.parsing")} /> : null}

          {preview && !previewMutation.isPending ? (
            <div className="space-y-3">
              <div className="flex flex-wrap items-center gap-2 text-sm text-stone-600">
                <Badge tone="stone">{preview.fileName}</Badge>
                <span>
                  {t("imports.validRows", { valid: validRows.length, total: rows.length })}
                </span>
              </div>
              {skippedSheets.length > 0 ? (
                <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-2 text-xs text-amber-700">
                  {t("imports.skippedSheets")}: {skippedSheets.join(", ")}
                </div>
              ) : null}
              {rows.length === 0 ? (
                <p className="text-sm text-stone-500">{t("imports.noRows")}</p>
              ) : (
                <div className="overflow-x-auto rounded-lg border border-stone-200">
                  <table className="w-full border-collapse">
                    <thead className="border-b border-stone-200 bg-stone-50">
                      <tr>
                        <Th>{t("imports.row")}</Th>
                        <Th>{t("imports.sheet")}</Th>
                        <Th>{t("imports.fields")}</Th>
                        <Th>{t("imports.valid")}</Th>
                        <Th>{t("imports.errors")}</Th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-stone-100">
                      {rows.map((r, idx) => (
                        <tr key={idx} className="align-top">
                          <Td>{r.rowNo}</Td>
                          <Td>
                            <span className="flex items-center gap-1.5">
                              {r.sheet}
                              <Badge tone="stone">{r.layout}</Badge>
                            </span>
                          </Td>
                          <Td className="max-w-[240px]">
                            <span className="block truncate" title={fieldsText(r.fields)}>
                              {fieldsText(r.fields)}
                            </span>
                          </Td>
                          <Td>
                            {r.valid ? (
                              <Badge tone="green">✓ {t("imports.valid")}</Badge>
                            ) : (
                              <Badge tone="red">✗ {t("imports.invalid")}</Badge>
                            )}
                          </Td>
                          <Td>
                            {r.errors.length === 0 ? (
                              <span className="text-stone-400">—</span>
                            ) : (
                              <ul className="list-disc space-y-0.5 pl-4 text-xs text-red-600">
                                {r.errors.map((err, i) => (
                                  <li key={i}>{err}</li>
                                ))}
                              </ul>
                            )}
                          </Td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          ) : null}
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-stone-200 px-5 py-3">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t("common.cancel")}
          </Button>
          <Button
            type="button"
            disabled={!preview || validRows.length === 0 || pending}
            onClick={() => {
              if (validRows.length > 0) {
                commitMutation.mutate(validRows);
              }
            }}
          >
            {commitMutation.isPending
              ? t("imports.importing")
              : t("imports.importValidRows", { count: validRows.length })}
          </Button>
        </div>
      </div>
    </div>
  );
}

function fieldsText(fields: Record<string, unknown>): string {
  return Object.entries(fields)
    .map(([k, v]) => `${k}=${String(v ?? "")}`)
    .join(" · ");
}
