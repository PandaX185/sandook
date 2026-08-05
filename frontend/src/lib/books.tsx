"use client";

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "./api";
import type { Book } from "./types";

interface BookContextValue {
  books: Book[];
  selectedBook: Book | null;
  selectedBookId: number | null;
  setSelectedBookId: (id: number) => void;
}

const BookContext = createContext<BookContextValue | null>(null);

export function BookProvider({ children }: { children: ReactNode }) {
  const { data: books = [] } = useQuery({
    queryKey: ["books"],
    queryFn: () => api<Book[]>("/api/v1/books"),
  });

  const [selectedBookId, setSelectedBookId] = useState<number | null>(null);

  // First book (Shop) by default; persist choice across reloads.
  useEffect(() => {
    if (selectedBookId === null && books.length > 0) {
      const stored = localStorage.getItem("sandook.book");
      const storedBook = books.find((b) => String(b.id) === stored);
      setSelectedBookId(storedBook?.id ?? books[0].id);
    }
  }, [books, selectedBookId]);

  const selectedBook =
    books.find((b) => b.id === selectedBookId) ?? books[0] ?? null;

  const value = useMemo(
    () => ({
      books,
      selectedBook,
      selectedBookId: selectedBook?.id ?? null,
      setSelectedBookId: (id: number) => {
        localStorage.setItem("sandook.book", String(id));
        setSelectedBookId(id);
      },
    }),
    [books, selectedBook],
  );

  return <BookContext.Provider value={value}>{children}</BookContext.Provider>;
}

export function useBook(): BookContextValue {
  const ctx = useContext(BookContext);
  if (!ctx) throw new Error("useBook must be used inside BookProvider");
  return ctx;
}
