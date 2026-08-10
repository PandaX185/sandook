"use client";

import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api";
import { fmtDate } from "@/lib/format";
import type { ParkingNotification } from "@/lib/types";
import { WarningBanner } from "@/components/ui";

/** In-app banner: parking bookings that are overdue or due within 7 days. */
export function ParkingNotifications({ bookId }: { bookId: number | null }) {
  const { t } = useTranslation();
  const notificationsQuery = useQuery({
    queryKey: ["parking-notifications", bookId],
    queryFn: () =>
      api<ParkingNotification[]>(`/api/v1/books/${bookId}/parking/notifications`),
    enabled: bookId !== null,
  });

  const notifications = notificationsQuery.data ?? [];
  if (notifications.length === 0) return null;

  const parts: string[] = [];
  const overdue = notifications.filter((n) => n.status === "OVERDUE");
  if (overdue.length > 0) {
    parts.push(
      `${t("parking.overdue")}: ${overdue.map((n) => `${n.plateNo} (${fmtDate(n.date)})`).join(", ")}`,
    );
  }
  const dueSoon = notifications.filter((n) => n.status === "DUE_SOON");
  if (dueSoon.length > 0) {
    parts.push(
      `${t("parking.dueSoon")}: ${dueSoon.map((n) => `${n.plateNo} (${fmtDate(n.date)})`).join(", ")}`,
    );
  }
  return <WarningBanner message={parts.join(" · ")} />;
}
