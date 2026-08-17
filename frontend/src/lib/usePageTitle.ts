"use client";

import { useEffect } from "react";
import { useTranslation } from "react-i18next";

const SITE_NAME = "Sandook — صندوق";

export function usePageTitle(key: string) {
  const { t } = useTranslation();
  useEffect(() => {
    document.title = `${t(key)} — ${SITE_NAME}`;
  }, [t, key]);
}
