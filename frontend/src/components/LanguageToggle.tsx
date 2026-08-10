"use client";

import { useState } from "react";
import { currentLang, setLang, type Lang } from "@/i18n";

export function LanguageToggle() {
  const [lang, setLangState] = useState<Lang>(() => currentLang());

  function toggle() {
    const next: Lang = lang === "en" ? "ar" : "en";
    setLang(next);
    setLangState(next);
  }

  return (
    <button
      onClick={toggle}
      className="rounded-lg border border-stone-300 bg-white px-2.5 py-1.5 text-sm font-medium text-stone-600 transition hover:bg-stone-100"
      title={lang === "en" ? "العربية" : "English"}
    >
      {lang === "en" ? "عربي" : "EN"}
    </button>
  );
}
