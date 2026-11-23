USE netbanking;

ALTER TABLE users
    ADD COLUMN credential_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS customer_transfers (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_account_id BIGINT UNSIGNED NOT NULL,
    destination_account_id BIGINT UNSIGNED NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    source_balance_after DECIMAL(19,2) NOT NULL,
    destination_balance_after DECIMAL(19,2) NOT NULL,
    request_key CHAR(36) NOT NULL UNIQUE,
    posted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (source_account_id <> destination_account_id AND amount > 0
           AND source_balance_after >= 0 AND destination_balance_after >= 0),
    INDEX ix_transfer_source (source_account_id, posted_at),
    INDEX ix_transfer_destination (destination_account_id, posted_at),
    FOREIGN KEY (source_account_id) REFERENCES bank_accounts(id),
    FOREIGN KEY (destination_account_id) REFERENCES bank_accounts(id)
) ENGINE=InnoDB;
