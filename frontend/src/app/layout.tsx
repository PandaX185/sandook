import type { Metadata } from "next";
import { Rubik } from "next/font/google";
import "./globals.css";
import { Providers } from "./providers";

const rubik = Rubik({
  variable: "--font-rubik",
  subsets: ["latin", "arabic"],
});

export const metadata: Metadata = {
  title: "Sandook — صندوق",
  description: "Cash box ledger: cash in hand, petty cash & parking",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      className={`${rubik.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
