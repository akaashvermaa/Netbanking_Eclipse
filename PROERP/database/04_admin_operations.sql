CREATE TABLE IF NOT EXISTS customer_profiles (
    customer_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(25),
    FOREIGN KEY (customer_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS bank_loans (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT UNSIGNED NOT NULL,
    principal DECIMAL(19,2) NOT NULL,
    outstanding DECIMAL(19,2) NOT NULL,
    disbursed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (principal > 0 AND outstanding >= 0 AND outstanding <= principal),
    KEY ix_bank_loans_account (account_id, id),
    FOREIGN KEY (account_id) REFERENCES bank_accounts(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS account_movements (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT UNSIGNED NOT NULL,
    admin_id BIGINT UNSIGNED NOT NULL,
    loan_id BIGINT UNSIGNED,
    kind ENUM('DEPOSIT','LOAN_DISBURSEMENT','LOAN_REPAYMENT') NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    balance_after DECIMAL(19,2) NOT NULL,
    request_key CHAR(36) NOT NULL UNIQUE,
    posted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (amount > 0 AND balance_after >= 0),
    FOREIGN KEY (account_id) REFERENCES bank_accounts(id),
    FOREIGN KEY (admin_id) REFERENCES users(id),
    FOREIGN KEY (loan_id) REFERENCES bank_loans(id)
) ENGINE=InnoDB;
