package com.meli.pagos.webhooks.repo;

import com.meli.pagos.webhooks.domain.PaymentStatus;
import com.meli.pagos.webhooks.domain.ProcessResult;
import com.meli.pagos.webhooks.domain.WebhookEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PaymentRepository {

    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserta el evento si su event_id es nuevo. Devuelve false si ya existia (reintento). */
    public boolean insertEventIfNew(WebhookEvent e, ProcessResult outcome) {
        int rows = jdbc.update("""
                INSERT INTO payment_event (event_id, payment_id, status, amount, currency, occurred_at, outcome)
                SELECT ?, ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (SELECT 1 FROM payment_event WHERE event_id = ?)
                """,
                e.eventId(), e.paymentId(), e.status().name(), e.amount(), e.currency(),
                Timestamp.from(e.timestamp()), outcome.name(), e.eventId());
        return rows == 1;
    }

    public void updateEventOutcome(String eventId, ProcessResult outcome) {
        jdbc.update("UPDATE payment_event SET outcome = ? WHERE event_id = ?", outcome.name(), eventId);
    }

    public void insertDelivery(WebhookEvent e, ProcessResult outcome) {
        jdbc.update("""
                INSERT INTO webhook_delivery (event_id, payment_id, status, occurred_at, outcome)
                VALUES (?, ?, ?, ?, ?)
                """,
                e.eventId(), e.paymentId(), e.status().name(), Timestamp.from(e.timestamp()), outcome.name());
    }

    /** Bloquea la fila del pago para serializar eventos concurrentes del mismo pago. */
    public Optional<PaymentStatus> lockStatus(String paymentId) {
        List<String> rows = jdbc.queryForList(
                "SELECT status FROM payment WHERE payment_id = ? FOR UPDATE", String.class, paymentId);
        return rows.stream().findFirst().map(PaymentStatus::valueOf);
    }

    public void insertPayment(WebhookEvent e) {
        jdbc.update("""
                INSERT INTO payment (payment_id, status, amount, currency, status_event_id, status_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                e.paymentId(), e.status().name(), e.amount(), e.currency(), e.eventId(),
                Timestamp.from(e.timestamp()));
    }

    public void advancePayment(WebhookEvent e) {
        jdbc.update("""
                UPDATE payment
                   SET status = ?, status_event_id = ?, status_at = ?, version = version + 1,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE payment_id = ?
                """,
                e.status().name(), e.eventId(), Timestamp.from(e.timestamp()), e.paymentId());
    }

    /** Acredita una sola vez por pago: la PK de payment_credit lo garantiza. */
    public boolean creditOnce(WebhookEvent e) {
        int rows = jdbc.update("""
                INSERT INTO payment_credit (payment_id, event_id, amount, currency)
                SELECT ?, ?, ?, ?
                WHERE NOT EXISTS (SELECT 1 FROM payment_credit WHERE payment_id = ?)
                """,
                e.paymentId(), e.eventId(), e.amount(), e.currency(), e.paymentId());
        return rows == 1;
    }

    public long countCredits(String paymentId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_credit WHERE payment_id = ?", Long.class, paymentId);
        return n == null ? 0 : n;
    }

    public Optional<Map<String, Object>> findPayment(String paymentId) {
        return jdbc.queryForList("""
                SELECT p.payment_id, p.status, p.amount, p.currency, p.status_at, p.updated_at,
                       (c.payment_id IS NOT NULL) AS credited
                  FROM payment p LEFT JOIN payment_credit c ON c.payment_id = p.payment_id
                 WHERE p.payment_id = ?
                """, paymentId).stream().findFirst();
    }

    public List<Map<String, Object>> findHistory(String paymentId) {
        return jdbc.queryForList("""
                SELECT event_id, status, occurred_at, received_at, outcome
                  FROM webhook_delivery
                 WHERE payment_id = ?
                 ORDER BY occurred_at, received_at
                """, paymentId);
    }
}
