package com.meli.pagos.webhooks.api;

import com.meli.pagos.webhooks.repo.PaymentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentRepository repo;

    public PaymentController(PaymentRepository repo) {
        this.repo = repo;
    }

    /** Estado consolidado: estado actual, monto y si ya se acredito. */
    @GetMapping("/{paymentId}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String paymentId) {
        return repo.findPayment(paymentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Historial cronologico de todo lo recibido, con el resultado de cada recepcion. */
    @GetMapping("/{paymentId}/events")
    public ResponseEntity<List<Map<String, Object>>> history(@PathVariable String paymentId) {
        List<Map<String, Object>> events = repo.findHistory(paymentId);
        return events.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(events);
    }
}
