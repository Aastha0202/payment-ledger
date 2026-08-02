-- V8__create_reconciliation_failures.sql
CREATE TABLE reconciliation_failures (
                                         id              UUID          PRIMARY KEY
                                                                                DEFAULT gen_random_uuid(),
                                         account_id      UUID          NOT NULL,
                                         cached_balance  DECIMAL(19,4) NOT NULL,
                                         calculated_balance DECIMAL(19,4) NOT NULL,
                                         discrepancy     DECIMAL(19,4) NOT NULL,
                                         detected_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
                                         resolved_at     TIMESTAMP,
                                         status          VARCHAR(20)   NOT NULL DEFAULT 'OPEN'
                                             CONSTRAINT reconciliation_status_check
                                                 CHECK (status IN ('OPEN', 'RESOLVED', 'INVESTIGATING'))
);

CREATE INDEX idx_reconciliation_account_id
    ON reconciliation_failures(account_id);
CREATE INDEX idx_reconciliation_status
    ON reconciliation_failures(status);