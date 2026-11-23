-- Cada recepcion de un webhook, incluidos los reintentos con el mismo event_id.
-- payment_event guarda un evento por event_id; esta tabla guarda cada vez que llego.
CREATE TABLE webhook_delivery (
    id           BIGSERIAL     PRIMARY KEY,
    event_id     VARCHAR(64)   NOT NULL,
    payment_id   VARCHAR(64)   NOT NULL,
    status       VARCHAR(16)   NOT NULL,
    occurred_at  TIMESTAMP     NOT NULL,
    received_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    outcome      VARCHAR(16)   NOT NULL   -- APPLIED | IGNORED_STALE | DUPLICATE
);

CREATE INDEX idx_webhook_delivery_payment ON webhook_delivery (payment_id, occurred_at, received_at);
