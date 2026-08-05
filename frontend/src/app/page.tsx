"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { Spinner } from "@/components/ui";

export default function Home() {
  const { status } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (status === "authed") router.replace("/dashboard");
    if (status === "guest") router.replace("/login");
  }, [status, router]);

  return (
    <div className="flex flex-1 items-center justify-center">
      <Spinner label="Sandook…" />
    </div>
  );
}
