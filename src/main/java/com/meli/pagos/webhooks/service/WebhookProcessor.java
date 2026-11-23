package com.meli.pagos.webhooks.service;

import com.meli.pagos.webhooks.domain.PaymentStatus;
import com.meli.pagos.webhooks.domain.ProcessResult;
import com.meli.pagos.webhooks.domain.WebhookEvent;
import com.meli.pagos.webhooks.repo.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class WebhookProcessor {

    private final PaymentRepository repo;

    public WebhookProcessor(PaymentRepository repo) {
        this.repo = repo;
    }

    /**
     * Procesa un webhook en una transaccion:
     * 1. si el event_id ya existe es un reintento: se registra la recepcion y no se aplica nada;
     * 2. se bloquea el pago (FOR UPDATE) para que dos eventos del mismo pago no se pisen;
     * 3. el estado solo avanza (PaymentStatus#canAdvanceTo); si no avanza, queda como IGNORED_STALE;
     * 4. la acreditacion de CAPTURED ocurre una sola vez por pago.
     */
    @Transactional
    public ProcessResult process(WebhookEvent e) {
        if (!repo.insertEventIfNew(e, ProcessResult.APPLIED)) {
            repo.insertDelivery(e, ProcessResult.DUPLICATE);
            return ProcessResult.DUPLICATE;
        }

        Optional<PaymentStatus> current = repo.lockStatus(e.paymentId());
        ProcessResult result;
        if (current.isEmpty()) {
            repo.insertPayment(e);
            result = ProcessResult.APPLIED;
        } else if (current.get().canAdvanceTo(e.status())) {
            repo.advancePayment(e);
            result = ProcessResult.APPLIED;
        } else {
            result = ProcessResult.IGNORED_STALE;
        }

        if (result == ProcessResult.APPLIED && e.status() == PaymentStatus.CAPTURED) {
            repo.creditOnce(e);
        }

        repo.updateEventOutcome(e.eventId(), result);
        repo.insertDelivery(e, result);
        return result;
    }
}
