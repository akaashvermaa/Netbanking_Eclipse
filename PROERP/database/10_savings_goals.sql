USE netbanking;

CREATE TABLE IF NOT EXISTS customer_savings_goals (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(60) NOT NULL,
    target_amount DECIMAL(19,2) NOT NULL,
    saved_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    closed_at TIMESTAMP NULL,
    request_key CHAR(36) NOT NULL UNIQUE,
    CHECK (target_amount > 0 AND saved_amount >= 0 AND saved_amount <= target_amount),
    CHECK (closed_at IS NULL OR saved_amount = 0),
    INDEX ix_savings_goals_account (account_id, closed_at),
    FOREIGN KEY (account_id) REFERENCES bank_accounts(id)
) ENGINE=InnoDB;

ALTER TABLE customer_postings
    MODIFY COLUMN kind ENUM('BILL_PAYMENT','DEPOSIT_OPEN','DEPOSIT_REDEEM','CUSTOMER_REPAYMENT',
                            'GOAL_CONTRIBUTION','GOAL_WITHDRAWAL') NOT NULL;
