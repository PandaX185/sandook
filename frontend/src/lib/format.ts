import i18n from "@/i18n";

/** Money is BIGINT minor units (fils) in the API. Users think in AED — convert at the edges. */

function currentLocale(): string {
  return i18n.language === "ar" ? "ar-AE" : "en-US";
}

export function filsToAed(minor: number): string {
  return (minor / 100).toLocaleString(currentLocale(), {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

export function filsToAedInput(minor: number): string {
  return (minor / 100).toFixed(2);
}

export function filsToAedWithCurrency(minor: number, currency = "AED"): string {
  return `${filsToAed(minor)} ${currency}`;
}

/** Parse a user-typed AED amount ("12.5", "12,5", "0") into fils. Returns null if invalid. */
export function aedToFils(input: string): number | null {
  const cleaned = input.trim().replace(",", ".");
  if (cleaned === "") return null;
  const value = Number(cleaned);
  if (!Number.isFinite(value) || value < 0) return null;
  return Math.round(value * 100);
}

/** Local date as YYYY-MM-DD (avoids UTC off-by-one from toISOString). */
export function todayISO(): string {
  const d = new Date();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${d.getFullYear()}-${m}-${day}`;
}

export function fmtDate(iso: string): string {
  const [y, m, d] = iso.split("-");
  const date = new Date(Number(y), Number(m) - 1, Number(d));
  return new Intl.DateTimeFormat(currentLocale(), {
    day: "numeric",
    month: "numeric",
    year: "numeric",
  }).format(date);
}
