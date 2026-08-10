"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useAuth } from "@/lib/auth";
import { Badge } from "./ui";
import { SandookIcon } from "./SandookIcon";
import { LanguageToggle } from "./LanguageToggle";

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
  const pathname = usePathname();
  const router = useRouter();
  const { t } = useTranslation();

  return (
    <div className="min-h-full bg-stone-100">
      <header className="sticky top-0 z-10 border-b border-stone-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3">
          <div className="flex items-center gap-2">
            <SandookIcon className="h-8 w-8" />
            <span className="text-lg font-bold text-stone-900">{t("app.name")}</span>
          </div>

          <nav className="flex flex-1 flex-wrap items-center gap-1">
            {NAV.filter((item) => !item.editorOnly || user?.role === "EDITOR").map((item) => {
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
            <LanguageToggle />
            <span className="hidden text-sm text-stone-600 sm:inline">{user?.username}</span>
            <Badge tone={user?.role === "EDITOR" ? "green" : "amber"}>{t(`roles.${user?.role}`)}</Badge>
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
    </div>
  );
}
