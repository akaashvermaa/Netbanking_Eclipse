USE netbanking;

ALTER TABLE users
    ADD COLUMN requested_accounts ENUM('NONE', 'SAVINGS', 'CURRENT', 'BOTH') NULL;
