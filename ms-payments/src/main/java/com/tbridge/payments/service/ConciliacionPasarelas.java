package com.tbridge.payments.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cada 10 segundos, se le pregunta a Khipu y a Mercado Pago por los cobros
 * abiertos. Ver {@link PaymentService#conciliarPendientes}.
 *
 * <p>Es lo que registra un pago cuando el deudor cierra la ventana sin volver,
 * y lo que reemplaza a los avisos de las pasarelas en local, donde no pueden
 * llegar. Mercado Pago, ademas, no devuelve al deudor a una direccion http.
 */
@Component
public class ConciliacionPasarelas {

    private static final Logger log = LoggerFactory.getLogger(ConciliacionPasarelas.class);

    private final PaymentService pagos;

    public ConciliacionPasarelas(PaymentService pagos) {
        this.pagos = pagos;
    }

    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void revisar() {
        int cerrados = pagos.conciliarPendientes();
        if (cerrados > 0) {
            log.info("Pasarelas: {} cobro(s) quedaron cerrados", cerrados);
        }
    }

    /** Cada 5 minutos, los vencidos del ultimo dia: por si alguno se pago igual. */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void revisarVencidos() {
        int registrados = pagos.revisarVencidos();
        if (registrados > 0) {
            log.warn("Pasarelas: {} cobro(s) vencido(s) se pagaron igual y quedaron registrados", registrados);
        }
    }
}
