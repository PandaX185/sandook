"use client";

import {
  createContext,
  useContext,
  useMemo,
  type ReactNode,
} from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "./api";
import type { Book } from "./types";

interface BookContextValue {
  books: Book[];
  selectedBook: Book | null;
  selectedBookId: number | null;
}

const BookContext = createContext<BookContextValue | null>(null);

export function BookProvider({ children }: { children: ReactNode }) {
  const { data: books = [] } = useQuery({
    queryKey: ["books"],
    queryFn: () => api<Book[]>("/api/v1/books"),
  });

  // Single-book workspace: always the first book (Shop). The header book
  // selector was removed — there is no book switching in the UI.
  const selectedBook = books[0] ?? null;
  const selectedBookId = selectedBook?.id ?? null;

  const value = useMemo(
    () => ({ books, selectedBook, selectedBookId }),
    [books, selectedBook, selectedBookId],
  );

  return <BookContext.Provider value={value}>{children}</BookContext.Provider>;
}

export function useBook(): BookContextValue {
  const ctx = useContext(BookContext);
  if (!ctx) throw new Error("useBook must be used inside BookProvider");
  return ctx;
}
