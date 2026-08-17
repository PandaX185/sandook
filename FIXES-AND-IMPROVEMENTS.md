# Fixes & Improvements — Sandook Frontend

> Actionable items only. Grouped by priority. Every line references a file:line.

---

## P0 — Localization Gaps (Hardcoded English)

These break the Arabic UI experience. They're quick wins.

| # | File | Line | Current | Fix |
|---|------|------|---------|-----|
| 1 | `src/app/cash/CashSheet.tsx` | 353 | `"Hide"` / `"Details"` | Use `t("common.hide")` / `t("common.details")` — add both keys to en.json & ar.json |
| 2 | `src/app/parking/Parking.tsx` | 372 | `Cash` (bill payment toggle) | Replace with `{t("payment.CASH")}` |
| 3 | `src/app/parking/Parking.tsx` | 383 | `Card` (bill payment toggle) | Replace with `{t("payment.CARD")}` |
| 4 | `src/app/parking/Parking.tsx` | 506 | `Edit` (bill row button) | Replace with `{t("common.edit")}` |
| 5 | `src/components/RequireAuth.tsx` | 19 | `"Loading…"` | Replace with `{t("common.loading")}` — add key to both locales |
| 6 | `src/app/audit/Audit.tsx` | 203–205 | `CREATE` / `UPDATE` / `DELETE` in action filter `<option>`s | Replace with `t("audit.actions.CREATE")` etc. — add keys |
| 7 | `src/app/audit/Audit.tsx` | 231 | `{entry.action}` raw string in badge | Use `t("audit.actions." + entry.action)` |
| 8 | `src/app/audit/Audit.tsx` | 237 | `"system"` | Use `t("audit.systemUser")` |
| 9 | `src/app/parking/Parking.tsx` | 839 | `"AED"` hardcoded currency | Replace with `currency` variable (already in scope) |

**Locale keys to add** (both `en.json` and `ar.json`):

```json
{
  "common.hide": "Hide" / "إخفاء",
  "common.details": "Details" / "التفاصيل",
  "common.loading": "Loading…" / "جارٍ التحميل…",
  "audit.actions.CREATE": "CREATE" / "إنشاء",
  "audit.actions.UPDATE": "UPDATE" / "تحديث",
  "audit.actions.DELETE": "DELETE" / "حذف",
  "audit.systemUser": "system" / "النظام"
}
```

---

## P0 — Locale-Aware Formatting

| # | File | Line | Issue | Fix |
|---|------|------|-------|-----|
| 10 | `src/lib/format.ts` | 35–38 | `fmtDate()` always renders `DD/MM/YYYY` — wrong for Arabic (should be `DD/MM/YYYY` in Arabic-locale numerals or at least respect user locale) | Accept optional `lang` param. Use `Intl.DateTimeFormat(locale, { dateStyle: "medium" })` when available, or keep manual formatting but use Arabic-Indic numerals when `lang === "ar"` |
| 11 | `src/lib/format.ts` | 4 | `filsToAed()` uses `.toLocaleString("en-US")` — Arabic users see Western digits | Accept optional `lang` param. Use `Intl.NumberFormat(locale, …)` to auto-format with the correct numbering system |

**Impact:** Every page that displays money or dates is affected (all 7 pages).

---

## P1 — Responsive Design

### Navigation (AppShell)

| # | Issue | Fix |
|---|-------|-----|
| 12 | Nav items wrap via `flex-wrap` — on phones (< 375px) with 7 items, the header becomes 2-3 rows tall, pushing content down | Add a mobile hamburger menu. Below `sm:` breakpoint, collapse nav into a slide-out drawer or bottom sheet. Keep the current `flex-wrap` for `sm:` and up. |

**Implementation sketch:**
- Add `useState(false)` for mobile menu open/close
- Below `sm:`: show hamburger icon (lucide `Menu`), hide nav links
- Drawer: fixed overlay with vertical nav links, close on tap-out or link click
- Above `sm:`: current behavior unchanged

### Wide Tables

| # | File | Min-width | Issue | Fix |
|---|------|-----------|-------|-----|
| 13 | `parking/Parking.tsx` | `min-w-[1100px]` (statement table, line 854) | Horizontally scrolls but is nearly unusable on phones | On mobile, switch to a **card-based layout** per day row instead of a table. Use a collapsible card with day summary; expand to show individual columns. Keep the table for `md:` and up. |
| 14 | `parking/Parking.tsx` | `min-w-[720px]` (bookings table, line 1248) | Same issue | Same solution: card layout on mobile, table on `md:`+ |
| 15 | `cash/CashSheet.tsx` | `min-w-160` (line 270) | 7 columns scroll | Card layout on mobile with condensed view |
| 16 | `petty-cash/PettyCash.tsx` | `min-w-140` (line 298) | 5 columns scroll | Card layout on mobile |

**Pattern:** Create a reusable `<ResponsiveTable>` wrapper that renders:
- Mobile (`< md`): stacked cards, one per row
- Desktop (`>= md`): current `<table>` layout

### Forms on Mobile

| # | Issue | Fix |
|---|-------|-----|
| 17 | `parking/Parking.tsx` (BillsTab form, lines 327–396) | All form fields sit in a single `flex-wrap` row — on mobile they stack awkwardly with fixed widths (`w-40`, `w-36`) | On mobile, use a 2-column grid (`grid-cols-2`). On desktop, keep the horizontal flex. |
| 18 | `parking/Parking.tsx` (BookingsTab form, lines 1131–1224) | Same issue — 6+ fields in a flex row | Same fix: `grid grid-cols-2 gap-3 sm:flex sm:flex-wrap sm:items-end sm:gap-3` |

---

## P1 — PWA Layer

### Required Files

| # | File | Action |
|---|------|--------|
| 19 | `public/manifest.webmanifest` | **Create.** Include: `name`, `short_name`, `start_url`, `display: "standalone"`, `background_color`, `theme_color`, `icons` (192, 512, maskable), `lang`, `dir` (dynamic based on i18n) |
| 20 | `public/sw.js` | **Create.** Cache-first for static assets (Next.js `_next/static`), network-first for API calls, offline fallback page |
| 21 | `src/app/layout.tsx` | Add `<link rel="manifest" href="/manifest.webmanifest">` and `<meta name="theme-color">` in `<head>` |
| 22 | `src/app/layout.tsx` | Register service worker: `navigator.serviceWorker?.register("/sw.js")` in a `<Script>` or useEffect in providers |
| 23 | `public/offline.html` | **Create.** Simple offline fallback page with Sandook icon + "You are offline" message (i18n-aware via query param or static bilingual) |

### Manifest Details

```json
{
  "name": "Sandook — صندوق",
  "short_name": "Sandook",
  "start_url": "/dashboard",
  "display": "standalone",
  "background_color": "#f5f5f4",
  "theme_color": "#059669",
  "lang": "en",
  "dir": "ltr",
  "icons": [
    { "src": "/icon-192.png", "sizes": "192x192", "type": "image/png" },
    { "src": "/icon-512.png", "sizes": "512x512", "type": "image/png" },
    { "src": "/icon-maskable.png", "sizes": "512x512", "type": "image/png", "purpose": "maskable" }
  ]
}
```

### Service Worker Strategy

```
Static assets (_next/static/*)  →  Cache-first, immutable
API calls (/api/*)              →  Network-first, cache fallback
HTML pages                      →  Network-first, offline fallback
Fonts (fonts.gstatic.com)      →  Cache-first
```

### PWA Install Prompt

| # | File | Action |
|---|------|--------|
| 24 | `src/components/AppShell.tsx` | Add an "Install App" button (lucide `Download` icon) that shows only when `beforeinstallprompt` event fires. Store the event, call `prompt()` on click. Hide after installed. |

---

## P2 — Code Quality

| # | File:Line | Issue | Fix |
|---|-----------|-------|-----|
| 25 | `cash/CashSheet.tsx:72` | `console.log("Saved cash day:", saved)` left in | Remove |
| 26 | `parking/Parking.tsx` (entire file) | 1414 lines — `BillsTab`, `StatementTab`, `BookingsTab` all in one file | Split into `BillsTab.tsx`, `StatementTab.tsx`, `BookingsTab.tsx` in `parking/` directory. Keep `Parking.tsx` as the shell that composes them. |
| 27 | `parking/Parking.tsx:151–156` | `PRESETS` array has `.label` property that's never used (the button uses `t()`) | Remove the `label` field from the PRESETS objects |

---

## P3 — Minor / Nice-to-Have

| # | File | Issue | Fix |
|---|------|-------|-----|
| 28 | `src/app/layout.tsx` | No per-page `<title>` or metadata | Add `generateMetadata()` or `useHead()` per page (e.g., "Cash Sheet — Sandook") |
| 29 | — | No `<meta name="apple-mobile-web-app-capable" content="yes">` | Add to layout head for iOS home screen support |
| 30 | `public/` | No `apple-touch-icon.png` in a proper PWA size (180×180 exists, but not referenced in manifest) | Add `"appleTouchIcon"` to manifest or keep as `<link>` in head |
| 31 | — | No error boundaries | Add `<ErrorBoundary>` wrapper in `providers.tsx` to catch rendering errors gracefully |
| 32 | — | No `not-found.tsx` page | Add `src/app/not-found.tsx` with a friendly 404 and link back to dashboard |
| 33 | — | No `loading.tsx` files for route-level Suspense | Add `loading.tsx` to each route segment for instant loading UI during navigation |

---

## Summary by Effort

| Effort | Items | Count |
|--------|-------|-------|
| **< 30 min** | #1–#9 (hardcoded strings), #25 (console.log), #27 (dead PRESETS label) | 11 |
| **~1 hour** | #10–#11 (locale-aware formatting), #26 (split Parking.tsx) | 3 |
| **2–4 hours** | #12 (mobile nav), #17–#18 (form responsive), #19–#24 (PWA layer) | 8 |
| **4–8 hours** | #13–#16 (responsive table → card pattern) | 4 |
| **< 30 min** | #28–##33 (metadata, error boundary, not-found, loading) | 6 |

**Total: 32 items.** Start with the P0 i18n fixes (#1–#11) — they're the highest impact for the least effort and directly serve the bilingual requirement.
