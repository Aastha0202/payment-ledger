CREATE TABLE transfer_sagas(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id UUID NOT NULL REFERENCES accounts(id),
    receiver_id UUID NOT NULL REFERENCES accounts(id),
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(20) NOT NULL DEFAULT 'INITIATED'
        CONSTRAINT transfer_sagas_status_check
            CHECK (status IN (
                              'INITIATED',
                              'DEBIT_PENDING',
                              'DEBIT_DONE',
                              'CREDIT_PENDING',
                              'COMPLETED',
                              'COMPENSATING',
                              'FAILED'
                )),
    retry_count INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(255),
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);


CREATE INDEX idx_sagas_status
    ON transfer_sagas(status);
CREATE INDEX idx_sagas_sender_id
    ON transfer_sagas(sender_id);
CREATE INDEX idx_sagas_receiver_id
    ON transfer_sagas(receiver_id);
