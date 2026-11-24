USE netbanking;

ALTER TABLE customer_transfers
    ADD COLUMN transfer_note VARCHAR(100) NULL,
    ADD COLUMN sender_name VARCHAR(100) NULL,
    ADD COLUMN recipient_name VARCHAR(100) NULL;
