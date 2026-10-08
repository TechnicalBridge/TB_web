package com.tbridge.payments.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cada 10 segundos, se le pregunta a Khipu, a Mercado Pago y a Transbank por
 * los cobros abiertos. Ver {@link PaymentService#conciliarPendientes} y
 * {@link PaymentService#conciliarWebpay}.
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
        int cerrados = pagos.conciliarPendientes() + webpay(false);
        if (cerrados > 0) {
            log.info("Pasarelas: {} cobro(s) quedaron cerrados", cerrados);
        }
    }

    /** Cada 5 minutos, los vencidos del ultimo dia: por si alguno se pago igual. */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void revisarVencidos() {
        int registrados = pagos.revisarVencidos() + webpay(true);
        if (registrados > 0) {
            log.warn("Pasarelas: {} cobro(s) vencido(s) se pagaron igual y quedaron registrados", registrados);
        }
    }

    /**
     * Webpay, cobro por cobro: cada uno en su propia transaccion, para que la
     * vuelta de un deudor espere solo a su cobro y un error no deshaga los demas.
     */
    private int webpay(boolean vencidos) {
        int cerrados = 0;
        for (Long id : pagos.webpayPorRevisar(vencidos)) {
            try {
                if (pagos.conciliarWebpay(id)) {
                    cerrados++;
                }
            } catch (RuntimeException e) {
                log.warn("No se pudo revisar el pago {} con Transbank: {}", id, e.getMessage());
            }
        }
        return cerrados;
    }
}
