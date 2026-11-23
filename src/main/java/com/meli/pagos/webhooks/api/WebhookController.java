package com.meli.pagos.webhooks.api;

import com.meli.pagos.webhooks.domain.ProcessResult;
import com.meli.pagos.webhooks.domain.WebhookEvent;
import com.meli.pagos.webhooks.service.WebhookProcessor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/webhooks/paygate")
public class WebhookController {

    private final WebhookProcessor processor;

    public WebhookController(WebhookProcessor processor) {
        this.processor = processor;
    }

    /**
     * Siempre 200 para un evento valido, tambien si es duplicado o llego tarde:
     * PayGate reintenta ante cualquier respuesta que no sea 2xx en menos de 2 s.
     * Un payload invalido devuelve 400 (lo resuelve la validacion de Spring).
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> receive(@Valid @RequestBody WebhookEvent event) {
        ProcessResult result = processor.process(event);
        return ResponseEntity.ok(Map.of("event_id", event.eventId(), "result", result.name()));
    }
}
