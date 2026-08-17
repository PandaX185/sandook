"use client";

import { Spinner } from "@/components/ui";

export default function Loading() {
  return (
    <div className="flex flex-1 items-center justify-center py-20">
      <Spinner />
    </div>
  );
}
