"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { FileDown, FileUp } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { useBook } from "@/lib/books";
import { downloadFile } from "@/lib/api";
import { Badge, Button } from "./ui";
import { SandookIcon } from "./SandookIcon";
import { LanguageToggle } from "./LanguageToggle";
import { ImportExcelDialog } from "./ImportExcelDialog";

const NAV = [
  { href: "/dashboard", labelKey: "nav.dashboard" },
  { href: "/cash", labelKey: "nav.cashSheet" },
  { href: "/petty-cash", labelKey: "nav.pettyCash" },
  { href: "/parking", labelKey: "nav.parking" },
  { href: "/transfers", labelKey: "nav.transfers" },
  { href: "/audit", labelKey: "nav.audit" },
  { href: "/users", labelKey: "nav.users", editorOnly: true },
];

export function AppShell({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  const { selectedBookId } = useBook();
  const pathname = usePathname();
  const router = useRouter();
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [importOpen, setImportOpen] = useState(false);
  const isEditor = user?.role === "EDITOR";

  return (
    <div className="min-h-full bg-stone-100">
      <header className="sticky top-0 z-10 border-b border-stone-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex max-w-full flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3">
          <div className="flex items-center gap-2">
            <SandookIcon className="h-8 w-8" />
            <span className="text-lg font-bold text-stone-900">{t("app.name")}</span>
          </div>

          <nav className="flex flex-1 flex-wrap items-center gap-1">
            {NAV.filter((item) => !item.editorOnly || isEditor).map((item) => {
              const active = pathname === item.href;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`rounded-lg px-3 py-1.5 text-sm font-medium transition ${active ? "bg-emerald-600 text-white" : "text-stone-600 hover:bg-stone-100"}`}
                >
                  {t(item.labelKey)}
                </Link>
              );
            })}
          </nav>

          <div className="flex items-center gap-2">
            {selectedBookId ? (
              <>
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() =>
                    downloadFile(
                      `/api/v1/books/${selectedBookId}/exports/all`,
                      "sandook_ledger.xlsx"
                    )
                  }
                >
                  <FileDown className="h-4 w-4" /> {t("common.export")}
                </Button>
                {isEditor ? (
                  <Button type="button" variant="secondary" onClick={() => setImportOpen(true)}>
                    <FileUp className="h-4 w-4" /> {t("common.import")}
                  </Button>
                ) : null}
              </>
            ) : null}
            <LanguageToggle />
            <span className="hidden text-sm text-stone-600 sm:inline">{user?.username}</span>
            <Badge tone={isEditor ? "green" : "amber"}>{t(`roles.${user?.role}`)}</Badge>
            <button
              onClick={async () => {
                await logout();
                router.replace("/login");
              }}
              className="rounded-lg px-3 py-1.5 text-sm font-medium text-stone-500 transition hover:bg-stone-100 hover:text-red-600"
            >
              {t("nav.logout")}
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-6 pb-16">{children}</main>

      {importOpen && selectedBookId ? (
        <ImportExcelDialog
          bookId={selectedBookId}
          invalidate={() => queryClient.invalidateQueries({ queryKey: ["books"] })}
          onClose={() => setImportOpen(false)}
        />
      ) : null}
    </div>
  );
}
