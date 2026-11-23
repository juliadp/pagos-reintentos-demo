# Notas de la charla con el PO (Mario)

## Formato del webhook
POST JSON: `event_id`, `payment_id`, `status` (PENDING|AUTHORIZED|CAPTURED|FAILED|REFUNDED), `amount`, `currency`, `timestamp`.

## Reintentos de PayGate
- Si no recibe 2xx en menos de 2 s, reintenta hasta 5 veces con backoff exponencial.
- En cada reintento el `event_id` es el mismo.
- Si el evento ya se proceso, esperan HTTP 200 sin volver a aplicar el cambio.
  -> hay que responder rapido (< 2 s) y la idempotencia va por `event_id`.

## Estados
- Finales: CAPTURED, FAILED, REFUNDED.
