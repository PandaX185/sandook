"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { FileDown, FileUp, Menu, X, Download } from "lucide-react";
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
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [deferredPrompt, setDeferredPrompt] = useState<Event | null>(null);
  const [installable, setInstallable] = useState(false);
  const isEditor = user?.role === "EDITOR";

  useEffect(() => {
    function onBeforeInstallPrompt(e: Event) {
      e.preventDefault();
      setDeferredPrompt(e);
      setInstallable(true);
    }
    function onAppInstalled() {
      setDeferredPrompt(null);
      setInstallable(false);
    }
    window.addEventListener("beforeinstallprompt", onBeforeInstallPrompt);
    window.addEventListener("appinstalled", onAppInstalled);
    return () => {
      window.removeEventListener("beforeinstallprompt", onBeforeInstallPrompt);
      window.removeEventListener("appinstalled", onAppInstalled);
    };
  }, []);

  async function handleInstall() {
    if (!deferredPrompt) return;
    (deferredPrompt as BeforeInstallPromptEvent).prompt();
    const { outcome } = await (deferredPrompt as BeforeInstallPromptEvent).userChoice;
    if (outcome === "dismissed") {
      setDeferredPrompt(null);
      setInstallable(false);
    }
  }

  const visibleNav = NAV.filter((item) => !item.editorOnly || isEditor);

  return (
    <div className="min-h-full bg-stone-100">
      <header className="sticky top-0 z-10 border-b border-stone-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex max-w-full items-center gap-x-3 px-4 py-3">
          <div className="flex items-center gap-2">
            <SandookIcon className="h-8 w-8" />
            <span className="text-lg font-bold text-stone-900">{t("app.name")}</span>
          </div>

          <nav className="hidden flex-1 flex-wrap items-center gap-1 sm:flex">
            {visibleNav.map((item) => {
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

          <div className="ms-auto flex items-center gap-2">
            {selectedBookId ? (
              <>
                <Button
                  type="button"
                  variant="secondary"
                  className="hidden sm:inline-flex"
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
                  <Button type="button" variant="secondary" className="hidden sm:inline-flex" onClick={() => setImportOpen(true)}>
                    <FileUp className="h-4 w-4" /> {t("common.import")}
                  </Button>
                ) : null}
              </>
            ) : null}
            <LanguageToggle />
            {installable ? (
              <button
                type="button"
                onClick={handleInstall}
                className="hidden rounded-lg p-1.5 text-stone-500 transition hover:bg-stone-100 hover:text-emerald-600 sm:inline-flex"
                title={t("common.installApp")}
              >
                <Download className="h-5 w-5" />
              </button>
            ) : null}
            <span className="hidden text-sm text-stone-600 sm:inline">{user?.username}</span>
            <Badge tone={isEditor ? "green" : "amber"} className="hidden sm:inline-flex">{t(`roles.${user?.role}`)}</Badge>
            <button
              onClick={async () => {
                await logout();
                router.replace("/login");
              }}
              className="rounded-lg px-3 py-1.5 text-sm font-medium text-stone-500 transition hover:bg-stone-100 hover:text-red-600"
            >
              {t("nav.logout")}
            </button>
            <button
              type="button"
              onClick={() => setMobileNavOpen(true)}
              className="rounded-lg p-1.5 text-stone-600 transition hover:bg-stone-100 sm:hidden"
              aria-label={t("common.menu")}
            >
              <Menu className="h-5 w-5" />
            </button>
          </div>
        </div>
      </header>

      {mobileNavOpen ? (
        <div className="fixed inset-0 z-50 sm:hidden" role="dialog">
          <div
            className="absolute inset-0 bg-stone-900/40"
            onClick={() => setMobileNavOpen(false)}
          />
          <nav className="absolute end-0 top-0 h-full w-72 overflow-y-auto bg-white shadow-xl">
            <div className="flex items-center justify-between border-b border-stone-200 px-4 py-3">
              <span className="text-lg font-bold text-stone-900">{t("app.name")}</span>
              <button
                type="button"
                onClick={() => setMobileNavOpen(false)}
                className="rounded-lg p-1.5 text-stone-400 hover:bg-stone-100 hover:text-stone-600"
                aria-label={t("common.close")}
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="space-y-1 p-3">
              {visibleNav.map((item) => {
                const active = pathname === item.href;
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    onClick={() => setMobileNavOpen(false)}
                    className={`block rounded-lg px-3 py-2.5 text-sm font-medium transition ${active ? "bg-emerald-600 text-white" : "text-stone-600 hover:bg-stone-50"}`}
                  >
                    {t(item.labelKey)}
                  </Link>
                );
              })}
            </div>
            <div className="border-t border-stone-200 p-3 space-y-2">
              <div className="flex items-center gap-2 px-1">
                <span className="text-sm text-stone-600">{user?.username}</span>
                <Badge tone={isEditor ? "green" : "amber"}>{t(`roles.${user?.role}`)}</Badge>
              </div>
              {selectedBookId ? (
                <div className="flex gap-2">
                  <Button
                    type="button"
                    variant="secondary"
                    className="flex-1"
                    onClick={() => {
                      setMobileNavOpen(false);
                      downloadFile(
                        `/api/v1/books/${selectedBookId}/exports/all`,
                        "sandook_ledger.xlsx"
                      );
                    }}
                  >
                    <FileDown className="h-4 w-4" /> {t("common.export")}
                  </Button>
                  {isEditor ? (
                    <Button
                      type="button"
                      variant="secondary"
                      className="flex-1"
                      onClick={() => {
                        setMobileNavOpen(false);
                        setImportOpen(true);
                      }}
                    >
                      <FileUp className="h-4 w-4" /> {t("common.import")}
                    </Button>
                  ) : null}
                </div>
              ) : null}
              {installable ? (
                <Button
                  type="button"
                  variant="secondary"
                  className="w-full"
                  onClick={() => {
                    handleInstall();
                    setMobileNavOpen(false);
                  }}
                >
                  <Download className="h-4 w-4" /> {t("common.installApp")}
                </Button>
              ) : null}
            </div>
          </nav>
        </div>
      ) : null}

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

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}
