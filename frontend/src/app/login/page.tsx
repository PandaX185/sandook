"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { useTranslation } from "react-i18next";
import { useAuth } from "@/lib/auth";
import { ApiError } from "@/lib/api";
import { usePageTitle } from "@/lib/usePageTitle";
import { Button, ErrorBanner, Field, Input } from "@/components/ui";
import { SandookIcon } from "@/components/SandookIcon";
import { LanguageToggle } from "@/components/LanguageToggle";

export default function LoginPage() {
  const { login } = useAuth();
  const router = useRouter();
  const { t } = useTranslation();
  usePageTitle("app.name");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(username.trim(), password);
      router.replace("/dashboard");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("login.serverError"));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex flex-1 items-center justify-center px-4">
      <div className="w-full max-w-sm rounded-2xl border border-stone-200 bg-white p-6 shadow-sm sm:p-8">
        <div className="mb-6 text-center">
          <SandookIcon className="mx-auto mb-3 h-12 w-12" />
          <h1 className="text-xl font-bold text-stone-900">{t("app.name")}</h1>
          <p className="mt-1 text-sm text-stone-500">{t("login.title")}</p>
        </div>

        <form onSubmit={onSubmit} className="space-y-4">
          <Field label={t("login.username")}>
            <Input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              autoFocus
              required
            />
          </Field>
          <Field label={t("login.password")}>
            <Input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
          </Field>

          {error ? <ErrorBanner message={error} /> : null}

          <Button type="submit" disabled={submitting} className="w-full">
            {submitting ? t("login.signingIn") : t("login.signIn")}
          </Button>
        </form>
      </div>
      <div className="fixed end-4 top-4">
        <LanguageToggle />
      </div>
    </div>
  );
}
