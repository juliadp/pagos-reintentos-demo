package com.meli.pagos.webhooks.domain;

/**
 * Estados de PayGate ordenados por avance. Un pago nunca retrocede a un estado de menor rango.
 * Supuesto (el PO no tiene la matriz de transiciones): REFUNDED es posterior a CAPTURED,
 * y FAILED es final para un pago que no llego a capturarse.
 */
public enum PaymentStatus {
    PENDING(0),
    AUTHORIZED(1),
    FAILED(2),
    CAPTURED(2),
    REFUNDED(3);

    private final int rank;

    PaymentStatus(int rank) { this.rank = rank; }

    public int rank() { return rank; }

    /** true si pasar de este estado a {@code next} es un avance que corresponde aplicar. */
    public boolean canAdvanceTo(PaymentStatus next) {
        if (this == next) return false;
        if (this == FAILED) return false;                     // final: nada lo saca
        if (this == REFUNDED) return false;                   // final
        if (next == REFUNDED) return this == CAPTURED;        // solo se reembolsa lo capturado
        return next.rank > this.rank;
    }
}
