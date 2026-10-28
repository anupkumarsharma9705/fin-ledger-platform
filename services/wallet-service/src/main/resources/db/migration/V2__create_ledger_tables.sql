ALTER TABLE wallet_service.wallets
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE TABLE IF NOT EXISTS wallet_service.ledger_entries (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    external_reference VARCHAR(128) UNIQUE,
    entry_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(512) NOT NULL,
    reversal_of_entry_id UUID REFERENCES wallet_service.ledger_entries (id),
    correlation_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    actor VARCHAR(128) NOT NULL,
    source VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    posted_at TIMESTAMPTZ,
    voided_at TIMESTAMPTZ,
    CONSTRAINT chk_ledger_entries_type CHECK (entry_type IN ('POSTING', 'AUTHORIZATION', 'REVERSAL', 'REFUND')),
    CONSTRAINT chk_ledger_entries_status CHECK (status IN ('PENDING', 'POSTED', 'VOIDED')),
    CONSTRAINT chk_ledger_entries_currency CHECK (char_length(currency) = 3)
);

CREATE INDEX IF NOT EXISTS idx_ledger_entries_created_at ON wallet_service.ledger_entries (created_at);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_reversal_of_entry_id ON wallet_service.ledger_entries (reversal_of_entry_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ledger_entries_reversal_type
    ON wallet_service.ledger_entries (reversal_of_entry_id, entry_type)
    WHERE reversal_of_entry_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS wallet_service.ledger_lines (
    id UUID PRIMARY KEY,
    entry_id UUID NOT NULL REFERENCES wallet_service.ledger_entries (id),
    wallet_id UUID NOT NULL REFERENCES wallet_service.wallets (id),
    direction VARCHAR(6) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_ledger_lines_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_lines_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_ledger_lines_currency CHECK (char_length(currency) = 3)
);

CREATE INDEX IF NOT EXISTS idx_ledger_lines_entry_id ON wallet_service.ledger_lines (entry_id);
CREATE INDEX IF NOT EXISTS idx_ledger_lines_wallet_id ON wallet_service.ledger_lines (wallet_id);
