package com.meli.pagos.webhooks;

import com.meli.pagos.webhooks.domain.PaymentStatus;
import com.meli.pagos.webhooks.domain.ProcessResult;
import com.meli.pagos.webhooks.domain.WebhookEvent;
import com.meli.pagos.webhooks.repo.PaymentRepository;
import com.meli.pagos.webhooks.service.WebhookProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WebhookProcessorTest {

    @Autowired WebhookProcessor processor;
    @Autowired PaymentRepository repo;

    private static WebhookEvent event(String eventId, String paymentId, PaymentStatus status, String ts) {
        return new WebhookEvent(eventId, paymentId, status, new BigDecimal("1500.00"), "ARS", Instant.parse(ts));
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    @Test
    void reintentoConElMismoEventIdNoSeAplicaDosVecesPeroQuedaEnElHistorial() {
        String pay = id();
        WebhookEvent captured = event(id(), pay, PaymentStatus.CAPTURED, "2024-05-01T10:00:00Z");

        assertThat(processor.process(captured)).isEqualTo(ProcessResult.APPLIED);
        assertThat(processor.process(captured)).isEqualTo(ProcessResult.DUPLICATE);
        assertThat(processor.process(captured)).isEqualTo(ProcessResult.DUPLICATE);

        assertThat(repo.countCredits(pay)).isEqualTo(1);
        assertThat(repo.findHistory(pay)).hasSize(3)
                .extracting(r -> r.get("OUTCOME") != null ? r.get("OUTCOME") : r.get("outcome"))
                .containsExactly("APPLIED", "DUPLICATE", "DUPLICATE");
    }

    @Test
    void eventoViejoQueLlegaTardeNoDegradaElEstado() {
        String pay = id();
        processor.process(event(id(), pay, PaymentStatus.CAPTURED, "2024-05-01T10:05:00Z"));
        ProcessResult late = processor.process(event(id(), pay, PaymentStatus.PENDING, "2024-05-01T10:00:00Z"));

        assertThat(late).isEqualTo(ProcessResult.IGNORED_STALE);
        assertThat(repo.findPayment(pay).orElseThrow()).containsEntry("STATUS", "CAPTURED");
        assertThat(repo.findHistory(pay)).hasSize(2);
    }

    @Test
    void eventosDesordenadosTerminanEnElEstadoMasAvanzado() {
        String pay = id();
        processor.process(event(id(), pay, PaymentStatus.AUTHORIZED, "2024-05-01T10:01:00Z"));
        processor.process(event(id(), pay, PaymentStatus.CAPTURED, "2024-05-01T10:02:00Z"));
        processor.process(event(id(), pay, PaymentStatus.PENDING, "2024-05-01T10:00:00Z"));

        assertThat(repo.findPayment(pay).orElseThrow()).containsEntry("STATUS", "CAPTURED");
        // el historial sale en orden cronologico de PayGate, no de llegada
        assertThat(repo.findHistory(pay))
                .extracting(r -> r.get("STATUS"))
                .containsExactly("PENDING", "AUTHORIZED", "CAPTURED");
    }

    @Test
    void dosCapturedConDistintoEventIdAcreditanUnaSolaVez() {
        String pay = id();
        processor.process(event(id(), pay, PaymentStatus.CAPTURED, "2024-05-01T10:00:00Z"));
        ProcessResult second = processor.process(event(id(), pay, PaymentStatus.CAPTURED, "2024-05-01T10:00:01Z"));

        assertThat(second).isEqualTo(ProcessResult.IGNORED_STALE);
        assertThat(repo.countCredits(pay)).isEqualTo(1);
    }

    @Test
    void unPagoFallidoNoSeCapturaDespues() {
        String pay = id();
        processor.process(event(id(), pay, PaymentStatus.FAILED, "2024-05-01T10:00:00Z"));
        ProcessResult captured = processor.process(event(id(), pay, PaymentStatus.CAPTURED, "2024-05-01T10:01:00Z"));

        assertThat(captured).isEqualTo(ProcessResult.IGNORED_STALE);
        assertThat(repo.countCredits(pay)).isZero();
    }

    @Test
    void refundSoloAvanzaDesdeCaptured() {
        String pay = id();
        processor.process(event(id(), pay, PaymentStatus.AUTHORIZED, "2024-05-01T10:00:00Z"));
        assertThat(processor.process(event(id(), pay, PaymentStatus.REFUNDED, "2024-05-01T10:01:00Z")))
                .isEqualTo(ProcessResult.IGNORED_STALE);

        processor.process(event(id(), pay, PaymentStatus.CAPTURED, "2024-05-01T10:02:00Z"));
        assertThat(processor.process(event(id(), pay, PaymentStatus.REFUNDED, "2024-05-01T10:03:00Z")))
                .isEqualTo(ProcessResult.APPLIED);
    }
}
