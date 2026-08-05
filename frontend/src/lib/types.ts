export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
  username: string;
  role: Role;
}

export type Role = "EDITOR" | "VIEWER";

export interface Me {
  id: number;
  username: string;
  role: Role;
  active: boolean;
}

export interface Book {
  id: number;
  name: string;
  currencyCode: string;
}

export type PettyCashType = "PUT" | "TAKE";

export interface CashDay {
  id: number;
  bookId: number;
  date: string;
  salesMinor: number;
  extraMinor: number;
  withdrawMinor: number;
  depositMinor: number;
  netCashMinor: number;
  balanceMinor: number;
  depositRemarks: string | null;
  ref: string | null;
  notes: string | null;
  warnings: string[];
}

export interface CashDayInput {
  date: string;
  salesMinor: number;
  extraMinor: number;
  withdrawMinor: number;
  depositMinor: number;
  depositRemarks?: string | null;
  ref?: string | null;
  notes?: string | null;
}

export interface PettyCashTx {
  id: number;
  bookId: number;
  date: string;
  description: string;
  type: PettyCashType;
  amountMinor: number;
  currencyCode: string;
  balanceMinor: number;
  linkedCashDayId: number | null;
  linkedCashDayWithdrawMinor: number | null;
}

export interface PettyCashInput {
  date: string;
  description: string;
  type: PettyCashType;
  amountMinor: number;
}

export interface BalanceResponse {
  bookId: number;
  balanceMinor: number;
}
