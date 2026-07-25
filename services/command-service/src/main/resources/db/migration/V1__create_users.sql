CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    address_line1  VARCHAR(255),
    city           VARCHAR(100),
    state          VARCHAR(100),
    pincode        VARCHAR(20),
    country        VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status        VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT users_status_check
            CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE INDEX idx_users_email ON users(email);