-- Estado consolidado de cada pago
CREATE TABLE payment (
    payment_id       VARCHAR(64)    PRIMARY KEY,
    status           VARCHAR(16)    NOT NULL,
    amount           NUMERIC(15,2)  NOT NULL,
    currency         CHAR(3)        NOT NULL,
    status_event_id  VARCHAR(64)    NOT NULL,   -- evento que fijo el estado actual
    status_at        TIMESTAMP      NOT NULL,   -- timestamp de ese evento
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Historial: todo evento recibido queda registrado, se aplique o no
CREATE TABLE payment_event (
    event_id     VARCHAR(64)   PRIMARY KEY,       -- idempotencia: PayGate reintenta con el mismo id
    payment_id   VARCHAR(64)   NOT NULL,
    status       VARCHAR(16)   NOT NULL,
    amount       NUMERIC(15,2) NOT NULL,
    currency     CHAR(3)       NOT NULL,
    occurred_at  TIMESTAMP     NOT NULL,          -- timestamp de PayGate
    received_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    outcome      VARCHAR(16)   NOT NULL           -- APPLIED | IGNORED_STALE
);

CREATE INDEX idx_payment_event_payment_occurred ON payment_event (payment_id, occurred_at);
