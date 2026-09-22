package com.tbridge.payments.service;

import com.tbridge.payments.domain.UfValue;
import com.tbridge.payments.repo.UfValueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mantiene cargada la UF: al arrancar y todos los dias en la manana de Chile.
 *
 * <p>Pide desde una semana atras hasta cuarenta dias adelante, asi que una
 * caida de varios dias del Banco Central, o de este servicio, no deja huecos:
 * la siguiente carga los rellena. Un cobro en UF nunca usa el valor de otro
 * dia (ver {@link UfService}), y por eso importa tener el mes por adelantado.
 */
@Service
public class UfLoader {

    private static final Logger log = LoggerFactory.getLogger(UfLoader.class);
    private static final ZoneId CHILE = ZoneId.of("America/Santiago");

    private final BancoCentralUf bancoCentral;
    private final UfValueRepository valores;

    public UfLoader(BancoCentralUf bancoCentral, UfValueRepository valores) {
        this.bancoCentral = bancoCentral;
        this.valores = valores;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        if (!bancoCentral.configurado()) {
            log.warn("Sin BCENTRAL_USER/BCENTRAL_PASS: la UF no se carga sola. "
                    + "Cargala con POST /internal/uf o configura las credenciales del Banco Central.");
            return;
        }
        cargar();
    }

    @Scheduled(cron = "${app.uf.cron:0 30 9 * * *}", zone = "America/Santiago")
    public void todosLosDias() {
        if (bancoCentral.configurado()) {
            cargar();
        }
    }

    /** Baja el rango y guarda lo publicado. Nunca lanza: una falla se reintenta manana. */
    public Map<String, Object> cargar() {
        LocalDate hoy = LocalDate.now(CHILE);
        Map<String, Object> resultado = new LinkedHashMap<>();
        try {
            List<UfValue> publicados = bancoCentral.obtener(hoy.minusDays(7), hoy.plusDays(40));
            valores.saveAll(publicados);
            LocalDate hasta = publicados.stream().map(UfValue::getDay).max(LocalDate::compareTo).orElse(null);
            resultado.put("cargados", publicados.size());
            resultado.put("hasta", hasta);
            log.info("UF del Banco Central: {} dias cargados, hasta el {}", publicados.size(), hasta);
            if (valores.findById(hoy).isEmpty()) {
                log.warn("El Banco Central no trajo la UF de hoy ({}): los cobros en UF se detienen", hoy);
            }
        } catch (InterruptedException corte) {
            Thread.currentThread().interrupt();
            resultado.put("error", "interrumpido");
        } catch (Exception fallo) {
            log.warn("No se pudo cargar la UF del Banco Central: {}", fallo.getMessage());
            resultado.put("error", fallo.getMessage());
        }
        return resultado;
    }
}
