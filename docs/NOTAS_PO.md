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

## Orden de entrega
- PayGate NO garantiza orden: un evento con timestamp posterior puede llegar antes.
- Si llega un evento de un estado anterior al actual (ej. PENDING despues de CAPTURED) **no se degrada** el estado.
- El evento se registra igual en el historial / auditoria.
- Matriz de transiciones: el PO no la tiene; confirmados como finales CAPTURED, FAILED y REFUNDED.

## Acreditacion
- La plata se mueve en la captura (CAPTURED): ese efecto ocurre **una sola vez por pago**.
- Si llegan dos CAPTURED para el mismo `payment_id` (aun con distinto `event_id`), el estado queda CAPTURED, se registran ambos y no se acredita de nuevo.

## Historial
- Tiene que listar **todos** los eventos recibidos: aplicados, fuera de orden y duplicados (reintentos).
- Como se marca cada uno queda a criterio tecnico. Decision: cada recepcion se guarda en `webhook_delivery`
  con su `outcome` (APPLIED, IGNORED_STALE, DUPLICATE).

## Reembolsos
- El PO no confirma si PayGate permite REFUNDED sobre algo que no sea CAPTURED.
- Supuesto: REFUNDED solo avanza desde CAPTURED; si llega sobre otro estado queda en el historial como IGNORED_STALE.
