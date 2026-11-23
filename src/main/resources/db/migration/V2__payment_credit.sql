-- Efecto de negocio de la captura: a lo sumo una acreditacion por pago
CREATE TABLE payment_credit (
    payment_id  VARCHAR(64)   PRIMARY KEY REFERENCES payment (payment_id),
    event_id    VARCHAR(64)   NOT NULL REFERENCES payment_event (event_id),
    amount      NUMERIC(15,2) NOT NULL,
    currency    CHAR(3)       NOT NULL,
    credited_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
