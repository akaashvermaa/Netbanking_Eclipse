CREATE TABLE IF NOT EXISTS bank_accounts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    customer_id BIGINT UNSIGNED NOT NULL,
    account_number CHAR(18) NOT NULL,
    account_type ENUM('SAVINGS', 'CURRENT') NOT NULL,
    status ENUM('ACTIVE', 'FROZEN') NOT NULL DEFAULT 'ACTIVE',
    balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    opened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_bank_account_number (account_number),
    UNIQUE KEY uq_customer_account_type (customer_id, account_type),
    KEY ix_bank_accounts_status (status),
    CONSTRAINT fk_bank_account_customer FOREIGN KEY (customer_id) REFERENCES users(id)
) ENGINE=InnoDB;
