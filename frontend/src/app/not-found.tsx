"use client";

import Link from "next/link";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui";

export default function NotFound() {
  const { t } = useTranslation();
  return (
    <div className="flex flex-1 flex-col items-center justify-center px-4 text-center">
      <p className="text-6xl font-bold text-stone-300">404</p>
      <h1 className="mt-4 text-xl font-semibold text-stone-900">{t("notFound.title")}</h1>
      <p className="mt-2 text-sm text-stone-500">{t("notFound.message")}</p>
      <Link href="/dashboard" className="mt-6">
        <Button>{t("notFound.backToDashboard")}</Button>
      </Link>
    </div>
  );
}
