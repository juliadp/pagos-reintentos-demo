# pagos-reintentos-demo

Microservicio en Java 17 + Spring Boot que recibe los webhooks de PayGate y mantiene el estado
consolidado de cada pago, tolerando reintentos, duplicados y eventos desordenados.

## Problema

PayGate reintenta los webhooks (hasta 5 veces, con backoff, si no recibe un 2xx en menos de 2 s)
y no garantiza el orden de entrega. Eso dejaba pagos en estados invalidos y generaba
acreditaciones dobles.

## Endpoints

| Metodo | Ruta | Que hace |
|---|---|---|
| `POST` | `/webhooks/paygate` | Recibe un evento. Responde **200** siempre que el payload sea valido (aplicado, viejo o duplicado) y **400** si no lo es |
| `GET` | `/payments/{paymentId}` | Estado consolidado: estado actual, monto, moneda y si ya se acredito |
| `GET` | `/payments/{paymentId}/events` | Historial cronologico de **todo** lo recibido, con el resultado de cada recepcion |

Payload (snake_case, como lo manda PayGate):

```json
{
  "event_id": "8c1f...",
  "payment_id": "a71b...",
  "status": "CAPTURED",
  "amount": 1500.00,
  "currency": "ARS",
  "timestamp": "2024-05-01T10:00:00Z"
}
```

## Como garantiza consistencia

1. **Idempotencia por `event_id`.** Si el evento ya existe es un reintento: se registra la recepcion
   como `DUPLICATE` y no se aplica nada.
2. **Bloqueo por pago.** `SELECT ... FOR UPDATE` sobre la fila del pago, dentro de la transaccion,
   para que dos eventos del mismo pago no se procesen a la vez.
3. **El estado nunca retrocede.** `PaymentStatus#canAdvanceTo` ordena los estados por avance
   (PENDING < AUTHORIZED < CAPTURED / FAILED < REFUNDED). Un evento que no avanza queda en el
   historial como `IGNORED_STALE`.
4. **Acreditacion una sola vez.** La captura mueve la plata; `payment_credit` tiene un registro por
   pago (PK `payment_id`), asi que un segundo CAPTURED, aunque traiga otro `event_id`, no acredita.
5. **Historial completo.** `webhook_delivery` guarda cada recepcion (incluidos los reintentos),
   ordenada por el timestamp de PayGate.

## Modelo de datos

Migraciones Flyway en `src/main/resources/db/migration`:

- `payment`: estado consolidado.
- `payment_event`: un registro por `event_id`.
- `payment_credit`: la acreditacion, a lo sumo una por pago.
- `webhook_delivery`: cada recepcion con su resultado.

## Supuestos y dudas abiertas

Las respuestas del PO estan en [`docs/NOTAS_PO.md`](docs/NOTAS_PO.md).

- **Transiciones:** el PO no tiene la matriz de PayGate. Confirmados como finales CAPTURED, FAILED y
  REFUNDED. Supuesto: FAILED no se captura despues; REFUNDED solo avanza desde CAPTURED.
  **Pendiente confirmar con PayGate.**
- **Firma del webhook:** no se valido la autenticidad del request (no hay definicion de PayGate);
  seria lo siguiente a agregar.
- **Monto:** se toma el del primer evento; no se valida que coincida entre eventos del mismo pago.

## Como correrlo

```bash
docker run -d --name pagos-db -p 5432:5432 \
  -e POSTGRES_USER=pagos -e POSTGRES_PASSWORD=pagos -e POSTGRES_DB=pagos postgres:16
mvn spring-boot:run
```

Ejemplo:

```bash
curl -X POST localhost:8080/webhooks/paygate -H 'Content-Type: application/json' \
  -d '{"event_id":"e1","payment_id":"p1","status":"CAPTURED","amount":1500,"currency":"ARS","timestamp":"2024-05-01T10:00:00Z"}'
curl localhost:8080/payments/p1
curl localhost:8080/payments/p1/events
```

## Tests

```bash
mvn test
```

`WebhookProcessorTest` (H2 en modo PostgreSQL) cubre: reintentos con el mismo `event_id`, evento
viejo que llega tarde, eventos desordenados, dos CAPTURED con distinto `event_id`, FAILED seguido de
CAPTURED y REFUNDED antes y despues de la captura.
