-- V2: domain schema — books, currencies, cash ledger, petty cash, parking, transfers, audit

CREATE TABLE currencies (
    code           VARCHAR(3)  PRIMARY KEY,
    name           VARCHAR(50) NOT NULL,
    symbol         VARCHAR(10) NOT NULL,
    decimal_places INT         NOT NULL DEFAULT 2
);

CREATE TABLE books (
    id            BIGSERIAL   PRIMARY KEY,
    name          VARCHAR(100) NOT NULL UNIQUE,
    currency_code VARCHAR(3)  NOT NULL REFERENCES currencies(code),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cash_days (
    id              BIGSERIAL   PRIMARY KEY,
    book_id         BIGINT      NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    date            DATE        NOT NULL,
    sales_minor     BIGINT      NOT NULL DEFAULT 0 CHECK (sales_minor >= 0),
    extra_minor     BIGINT      NOT NULL DEFAULT 0 CHECK (extra_minor >= 0),
    withdraw_minor  BIGINT      NOT NULL DEFAULT 0 CHECK (withdraw_minor >= 0),
    deposit_minor   BIGINT      NOT NULL DEFAULT 0 CHECK (deposit_minor >= 0),
    deposit_remarks VARCHAR(255),
    "ref"           VARCHAR(100),
    notes           TEXT,
    entered_by      BIGINT      REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (book_id, date)
);

CREATE INDEX idx_cash_days_book_date ON cash_days(book_id, date);

CREATE TABLE petty_cash_transactions (
    id            BIGSERIAL   PRIMARY KEY,
    book_id       BIGINT      NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    date          DATE        NOT NULL,
    description   VARCHAR(255) NOT NULL,
    type          VARCHAR(10) NOT NULL CHECK (type IN ('PUT', 'TAKE')),
    amount_minor  BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency_code VARCHAR(3)  NOT NULL REFERENCES currencies(code),
    entered_by    BIGINT      REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_petty_cash_book_date ON petty_cash_transactions(book_id, date);

CREATE TABLE parking_bills (
    id             BIGSERIAL   PRIMARY KEY,
    book_id        BIGINT      NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    plate_no       VARCHAR(20) NOT NULL,
    amount_minor   BIGINT      NOT NULL CHECK (amount_minor > 0),
    payment_method VARCHAR(10) NOT NULL CHECK (payment_method IN ('CASH', 'CARD')),
    billed_at      DATE        NOT NULL,
    entered_by     BIGINT      REFERENCES users(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_parking_bills_book_date ON parking_bills(book_id, billed_at);

CREATE TABLE parking_cash_moves (
    id          BIGSERIAL   PRIMARY KEY,
    book_id     BIGINT      NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    date        DATE        NOT NULL,
    type        VARCHAR(20) NOT NULL CHECK (type IN ('OPENING', 'TRANSFER_TO_SHOP', 'SALARY', 'EXPENSE', 'CLOSING')),
    amount_minor BIGINT     NOT NULL CHECK (amount_minor > 0),
    description VARCHAR(255),
    entered_by  BIGINT      REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_parking_moves_book_date ON parking_cash_moves(book_id, date);

CREATE TABLE parking_salary_payments (
    id           BIGSERIAL   PRIMARY KEY,
    move_id      BIGINT      NOT NULL REFERENCES parking_cash_moves(id) ON DELETE CASCADE,
    person       VARCHAR(100) NOT NULL,
    amount_minor BIGINT      NOT NULL CHECK (amount_minor > 0)
);

CREATE TABLE parking_bookings (
    id                BIGSERIAL   PRIMARY KEY,
    book_id           BIGINT      NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    plate_no          VARCHAR(20) NOT NULL,
    monthly_rate_minor BIGINT     NOT NULL CHECK (monthly_rate_minor >= 0),
    renewal_month     DATE        NOT NULL,
    active            BOOLEAN     NOT NULL DEFAULT TRUE,
    entered_by        BIGINT      REFERENCES users(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_parking_bookings_book ON parking_bookings(book_id, renewal_month);

CREATE TABLE transfers (
    id            BIGSERIAL   PRIMARY KEY,
    from_book_id  BIGINT      NOT NULL REFERENCES books(id),
    to_book_id    BIGINT      NOT NULL REFERENCES books(id),
    date          DATE        NOT NULL,
    amount_minor  BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency_code VARCHAR(3)  NOT NULL REFERENCES currencies(code),
    "ref"         VARCHAR(100),
    entered_by    BIGINT      REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (from_book_id <> to_book_id)
);

CREATE TABLE audit_log (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      REFERENCES users(id),
    action     VARCHAR(50) NOT NULL,
    entity     VARCHAR(50) NOT NULL,
    entity_id  BIGINT,
    old_value  JSONB,
    new_value  JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_entity ON audit_log(entity, entity_id);

-- Seed: AED + the two books (generic names, brand-agnostic per README cleanup)
INSERT INTO currencies (code, name, symbol, decimal_places) VALUES ('AED', 'UAE Dirham', 'د.إ', 2);
INSERT INTO books (name, currency_code) VALUES ('Shop', 'AED'), ('Parking', 'AED');
