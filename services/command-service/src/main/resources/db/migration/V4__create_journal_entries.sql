CREATE TABLE journal_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(id),
    transfer_id UUID NOT NULL REFERENCES transfer_sagas(id),
    entry_type VARCHAR(20) NOT NULL
        CONSTRAINT journal_entries_entry_type_check
            CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount DECIMAL(19,4) NOT NULL,
    CONSTRAINT journal_amount_positive
        CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    description VARCHAR(255),
    reference_id UUID,
    reference_type VARCHAR(50)
);

CREATE INDEX idx_journal_account_id
    ON journal_entries(account_id);
CREATE INDEX idx_journal_transfer_id
    ON journal_entries(transfer_id);

CREATE RULE journal_entries_no_update
    AS ON UPDATE TO journal_entries
                     DO INSTEAD NOTHING;

CREATE RULE journal_entries_no_delete
    AS ON DELETE TO journal_entries
    DO INSTEAD NOTHING;