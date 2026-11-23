USE netbanking;

CREATE TABLE IF NOT EXISTS loan_applications (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT UNSIGNED NOT NULL,
    amount DECIMAL(19,2) NOT NULL CHECK (amount > 0),
    purpose VARCHAR(40) NOT NULL,
    status ENUM('PENDING','APPROVED','DECLINED') NOT NULL DEFAULT 'PENDING',
    request_key CHAR(36) NOT NULL UNIQUE,
    loan_id BIGINT UNSIGNED UNIQUE,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at TIMESTAMP NULL,
    decided_by BIGINT UNSIGNED,
    INDEX ix_application_queue (status, submitted_at),
    FOREIGN KEY (account_id) REFERENCES bank_accounts(id),
    FOREIGN KEY (loan_id) REFERENCES bank_loans(id),
    FOREIGN KEY (decided_by) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS customer_payees (
    customer_id BIGINT UNSIGNED NOT NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    label VARCHAR(60) NOT NULL,
    PRIMARY KEY (customer_id, account_id),
    FOREIGN KEY (customer_id) REFERENCES users(id),
    FOREIGN KEY (account_id) REFERENCES bank_accounts(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS customer_cards (
    account_id BIGINT UNSIGNED PRIMARY KEY,
    last_four CHAR(4) NOT NULL,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    daily_limit DECIMAL(10,2) NOT NULL DEFAULT 25000.00,
    FOREIGN KEY (account_id) REFERENCES bank_accounts(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS customer_deposits (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT UNSIGNED NOT NULL,
    principal DECIMAL(19,2) NOT NULL,
    payout DECIMAL(19,2) NOT NULL,
    maturity_date DATE NOT NULL,
    redeemed BOOLEAN NOT NULL DEFAULT FALSE,
    opened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES bank_accounts(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS customer_postings (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT UNSIGNED NOT NULL,
    kind ENUM('BILL_PAYMENT','DEPOSIT_OPEN','DEPOSIT_REDEEM','CUSTOMER_REPAYMENT') NOT NULL,
    description VARCHAR(120) NOT NULL,
    amount DECIMAL(19,2) NOT NULL CHECK (amount > 0),
    balance_after DECIMAL(19,2) NOT NULL CHECK (balance_after >= 0),
    request_key CHAR(36) NOT NULL UNIQUE,
    posted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX ix_customer_posting_activity (account_id, posted_at),
    FOREIGN KEY (account_id) REFERENCES bank_accounts(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS customer_service_requests (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT UNSIGNED NOT NULL,
    category ENUM('CHEQUE_BOOK','CONTACT_UPDATE','TRANSACTION_REVIEW') NOT NULL,
    details VARCHAR(240) NOT NULL,
    status ENUM('OPEN','RESOLVED') NOT NULL DEFAULT 'OPEN',
    request_key CHAR(36) NOT NULL UNIQUE,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    INDEX ix_service_queue (status, submitted_at),
    FOREIGN KEY (customer_id) REFERENCES users(id)
) ENGINE=InnoDB;
