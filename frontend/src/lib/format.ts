/** Money is BIGINT minor units (fils) in the API. Users think in AED — convert at the edges. */

export function filsToAed(minor: number): string {
  return (minor / 100).toLocaleString("en-US", {
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
  return `${d}/${m}/${y}`;
}
