import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import en from "./locales/en.json";
import ar from "./locales/ar.json";
import {
  applyDocumentLang,
  readSavedLang,
  LANG_STORAGE_KEY,
  type Lang,
} from "./i18n/lang";

export type { Lang };
export const LANGS: Lang[] = ["en", "ar"];

export function setLang(lng: Lang) {
  i18n.changeLanguage(lng);
  localStorage.setItem(LANG_STORAGE_KEY, lng);
  applyDocumentLang(lng);
}

export function currentLang(): Lang {
  return (i18n.language === "ar" ? "ar" : "en") as Lang;
}

applyDocumentLang(readSavedLang());

i18n.use(initReactI18next).init({
  resources: { en: { translation: en }, ar: { translation: ar } },
  lng: readSavedLang(),
  fallbackLng: "en",
  interpolation: { escapeValue: false },
});

export default i18n;
