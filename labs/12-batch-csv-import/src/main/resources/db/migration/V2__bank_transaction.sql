-- The import target. The primary key on the bank's transaction id is what makes a restart that
-- re-wrote an already committed chunk fail loudly instead of silently duplicating money movements.
CREATE TABLE bank_transaction (
    id          VARCHAR(40)    PRIMARY KEY,
    account_id  VARCHAR(34)    NOT NULL,
    amount      NUMERIC(19, 2) NOT NULL,
    currency    CHAR(3)        NOT NULL,
    booked_on   DATE           NOT NULL,
    imported_at TIMESTAMPTZ    NOT NULL DEFAULT now()
);
