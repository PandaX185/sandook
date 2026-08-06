-- V3: link auto-created parking cash moves back to their transfer,
-- so the transfer module can reverse them reliably (no matching by amount/date).
ALTER TABLE parking_cash_moves ADD COLUMN transfer_id BIGINT REFERENCES transfers(id) ON DELETE SET NULL;
CREATE INDEX idx_parking_moves_transfer ON parking_cash_moves(transfer_id);
