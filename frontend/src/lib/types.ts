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

// --- Parking ---

export type PaymentMethod = "CASH" | "CARD";

export interface ParkingBill {
  id: number;
  bookId: number;
  plateNo: string;
  amountMinor: number;
  paymentMethod: PaymentMethod;
  billedAt: string;
  enteredBy: number | null;
  createdAt: string;
}

export interface ParkingBillInput {
  plateNo: string;
  amountMinor: number;
  paymentMethod: PaymentMethod;
  billedAt: string;
}

export interface ParkingBillSummary {
  bookId: number;
  from: string | null;
  to: string | null;
  cashMinor: number;
  cardMinor: number;
  totalMinor: number;
  count: number;
}

export type ParkingCashMoveType =
  | "OPENING"
  | "TRANSFER_TO_SHOP"
  | "SALARY"
  | "EXPENSE"
  | "CLOSING";

export interface SalaryPayment {
  id: number;
  person: string;
  amountMinor: number;
}

export interface ParkingCashMove {
  id: number;
  bookId: number;
  date: string;
  type: ParkingCashMoveType;
  amountMinor: number;
  description: string | null;
  salaryPayments: SalaryPayment[];
  balanceMinor: number;
  enteredBy: number | null;
  createdAt: string;
}

export interface ParkingCashMoveInput {
  date: string;
  type: ParkingCashMoveType;
  amountMinor: number;
  description?: string | null;
  salaryPayments?: { person: string; amountMinor: number }[];
}

export interface ParkingStatement {
  bookId: number;
  days: {
    date: string;
    openingMinor: number;
    cashBillsMinor: number;
    transfersToShopMinor: number;
    salariesMinor: number;
    expensesMinor: number;
    netOutMinor: number;
    closingMinor: number;
    warnings: string[];
  }[];
}

export interface ParkingBooking {
  id: number;
  bookId: number;
  plateNo: string;
  monthlyRateMinor: number;
  renewalMonth: string;
  active: boolean;
  due: boolean;
  enteredBy: number | null;
  createdAt: string;
}

export interface ParkingBookingInput {
  plateNo: string;
  monthlyRateMinor: number;
  renewalMonth: string;
  active?: boolean;
}

// --- Transfers ---

export interface Transfer {
  id: number;
  fromBookId: number;
  toBookId: number;
  date: string;
  amountMinor: number;
  currencyCode: string;
  ref: string | null;
  linkedParkingMove: boolean;
  linkedMoveId: number | null;
  linkedCashDayId: number | null;
  linkedCashDayExtraMinor: number | null;
  enteredBy: number | null;
  createdAt: string;
}

export interface TransferInput {
  fromBookId: number;
  toBookId: number;
  date: string;
  amountMinor: number;
  ref?: string | null;
  linkParkingMove?: boolean;
}

export interface TransferUpdateInput {
  date: string;
  amountMinor: number;
  ref?: string | null;
}

// --- Audit ---

export type AuditAction = "CREATE" | "UPDATE" | "DELETE";

export interface AuditEntry {
  id: number;
  username: string | null;
  action: AuditAction;
  entity: string;
  entityId: number;
  oldValue: unknown | null;
  newValue: unknown | null;
  createdAt: string;
}

// --- Users ---

export interface User {
  id: number;
  username: string;
  role: Role;
  active: boolean;
}

export interface CreateUserRequest {
  username: string;
  password: string;
  role: Role;
}

export interface UpdateUserRequest {
  role?: Role;
  active?: boolean;
  password?: string;
}
