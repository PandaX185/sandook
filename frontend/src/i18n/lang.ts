export type Lang = "en" | "ar";
export const LANGS: Lang[] = ["en", "ar"];
export const LANG_STORAGE_KEY = "sandook.lang";

export function readSavedLang(): Lang {
  if (typeof window === "undefined") return "en";
  const saved = localStorage.getItem(LANG_STORAGE_KEY);
  return saved === "ar" ? "ar" : "en";
}

export function applyDocumentLang(lng: Lang) {
  if (typeof document === "undefined") return;
  document.documentElement.lang = lng;
  document.documentElement.dir = lng === "ar" ? "rtl" : "ltr";
}

/** Server-safe: SSR always renders en; the client applies the saved lang via applyDocumentLang(). */
export function serverLang(): Lang {
  return "en";
}
