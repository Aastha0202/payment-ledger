CREATE TABLE outbox (
    id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CONSTRAINT outbox_status_check
            CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
    retry_count INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_status
    ON outbox(status);
CREATE INDEX idx_outbox_created_at
    ON outbox(created_at);