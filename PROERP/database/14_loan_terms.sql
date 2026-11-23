USE netbanking;

ALTER TABLE loan_applications
    ADD COLUMN term_months SMALLINT UNSIGNED NULL,
    ADD COLUMN annual_rate DECIMAL(5,2) NULL;

ALTER TABLE bank_loans
    ADD COLUMN term_months SMALLINT UNSIGNED NULL,
    ADD COLUMN annual_rate DECIMAL(5,2) NULL,
    ADD COLUMN interest_due DECIMAL(19,2) NOT NULL DEFAULT 0.00;
