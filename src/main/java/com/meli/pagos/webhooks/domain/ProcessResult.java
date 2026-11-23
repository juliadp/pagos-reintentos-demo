package com.meli.pagos.webhooks.domain;

public enum ProcessResult {
    APPLIED,          // cambio el estado del pago
    IGNORED_STALE,    // quedo en el historial, pero no avanza el estado
    DUPLICATE         // event_id ya recibido: reintento de PayGate
}
