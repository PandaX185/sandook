"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { useAuth } from "@/lib/auth";
import { useBook } from "@/lib/books";
import { Badge, Select } from "./ui";

const NAV = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/cash", label: "Cash sheet" },
  { href: "/petty-cash", label: "Petty cash" },
  { href: "/parking", label: "Parking" },
  { href: "/transfers", label: "Transfers" },
  { href: "/audit", label: "Audit" },
  { href: "/users", label: "Users", editorOnly: true },
];

export function AppShell({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  const { books, selectedBook, selectedBookId, setSelectedBookId } = useBook();
  const pathname = usePathname();
  const router = useRouter();

  return (
    <div className="min-h-full bg-stone-100">
      <header className="sticky top-0 z-10 border-b border-stone-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3">
          <div className="flex items-center gap-2">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-600 text-base font-bold text-white">
              ص
            </span>
            <span className="text-lg font-bold text-stone-900">Sandook</span>
          </div>

          {books.length > 0 && selectedBook ? (
            <Select
              aria-label="Book"
              value={selectedBookId ?? undefined}
              onChange={(e) => setSelectedBookId(Number(e.target.value))}
              className="w-40 !py-1.5 text-sm"
            >
              {books.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name} · {b.currencyCode}
                </option>
              ))}
            </Select>
          ) : null}

          <nav className="flex flex-1 flex-wrap items-center gap-1">
            {NAV.filter((item) => !item.editorOnly || user?.role === "EDITOR").map((item) => {
              const active = pathname === item.href;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`rounded-lg px-3 py-1.5 text-sm font-medium transition ${
                    active
                      ? "bg-emerald-600 text-white"
                      : "text-stone-600 hover:bg-stone-100"
                  }`}
                >
                  {item.label}
                </Link>
              );
            })}
          </nav>

          <div className="flex items-center gap-2">
            <span className="hidden text-sm text-stone-600 sm:inline">
              {user?.username}
            </span>
            <Badge tone={user?.role === "EDITOR" ? "green" : "amber"}>
              {user?.role}
            </Badge>
            <button
              onClick={async () => {
                await logout();
                router.replace("/login");
              }}
              className="rounded-lg px-3 py-1.5 text-sm font-medium text-stone-500 transition hover:bg-stone-100 hover:text-red-600"
            >
              Logout
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-6 pb-16">{children}</main>
    </div>
  );
}
