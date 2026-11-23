package com.meli.pagos.webhooks.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

/** Payload de PayGate (snake_case en el JSON, via la estrategia de Jackson). */
public record WebhookEvent(
        @NotBlank String eventId,
        @NotBlank String paymentId,
        @NotNull PaymentStatus status,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String currency,
        @NotNull Instant timestamp) {
}
