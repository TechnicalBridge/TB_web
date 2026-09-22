package com.tbridge.debt.integracion;

import com.tbridge.debt.domain.Campaign;
import com.tbridge.debt.domain.Debt;
import com.tbridge.debt.domain.DebtEvent;
import com.tbridge.debt.repo.CampaignRepository;
import com.tbridge.debt.repo.DebtEventRepository;
import com.tbridge.debt.repo.DebtRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * El avance de cada campana, una vez al dia (contrato, seccion 8.2).
 *
 * <p>Es lo que la agencia no podia medir por su cuenta: hasta donde llega su
 * embudo termina en el mensaje enviado, porque el pago ocurre fuera de su
 * producto (documento de APOFYX, seccion 11.3). Aca ocurre dentro, asi que
 * DataBridge puede decirle cuanto se pago y cuanto se recupero.
 *
 * <p>Va lo que DataBridge mide de verdad. Lo que no mide —si el mensaje se
 * entrego, si respondieron, las bajas— no viaja en cero: viaja ausente, para
 * que nadie lea un cero como "ninguno" cuando significa "no lo se".
 *
 * <p>Los pesos y las UF se informan por separado, como en todo el resto: una
 * suma de ambos no significaria nada.
 */
@Service
public class CampanaAvanceService {

    private static final Logger log = LoggerFactory.getLogger(CampanaAvanceService.class);
    private static final ZoneId CHILE = ZoneId.of("America/Santiago");

    private final CampaignRepository campanas;
    private final DebtRepository debts;
    private final DebtEventRepository eventos;
    private final EventosService avisos;

    public CampanaAvanceService(CampaignRepository campanas, DebtRepository debts,
                                DebtEventRepository eventos, EventosService avisos) {
        this.campanas = campanas;
        this.debts = debts;
        this.eventos = eventos;
        this.avisos = avisos;
    }

    @Scheduled(cron = "${app.eventos.avance-cron:0 0 8 * * *}", zone = "America/Santiago")
    public void deTodasLasCampanas() {
        publicarTodas();
    }

    @Transactional
    public List<Map<String, Object>> publicarTodas() {
        List<Map<String, Object>> hechas = new ArrayList<>();
        for (Campaign campana : campanas.findByStatus(Campaign.Status.running)) {
            Map<String, Object> datos = avance(campana);
            int avisados = avisos.publicarDeCampana(campana, datos, Instant.now());
            Map<String, Object> fila = new LinkedHashMap<>(datos);
            fila.put("avisados", avisados);
            hechas.add(fila);
            log.info("Avance de {}: {} deudas, {} pagos", campana.getExternalId(),
                    datos.get("deudas"), datos.get("pagos"));
        }
        return hechas;
    }

    /** El acumulado de la campana hasta hoy. */
    public Map<String, Object> avance(Campaign campana) {
        List<Debt> cartera = debts.findByCampaign(campana);
        Map<DebtEvent.Type, Integer> cuantos = new EnumMap<>(DebtEvent.Type.class);
        BigDecimal enPesos = BigDecimal.ZERO;
        BigDecimal enUf = BigDecimal.ZERO;

        if (!cartera.isEmpty()) {
            for (DebtEvent evento : eventos.findByDebtIn(cartera)) {
                cuantos.merge(evento.getType(), 1, Integer::sum);
                if (evento.getType() == DebtEvent.Type.payment_applied && evento.getAmount() != null) {
                    if (evento.getCurrency() == Debt.Currency.UF) {
                        enUf = enUf.add(evento.getAmount());
                    } else {
                        enPesos = enPesos.add(evento.getAmount());
                    }
                }
            }
        }

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("campana_id_externo", campana.getExternalId());
        datos.put("fecha_corte", LocalDate.now(CHILE).toString());
        datos.put("deudas", cartera.size());
        datos.put("enviados", cuantos.getOrDefault(DebtEvent.Type.code_sent, 0));
        datos.put("ingresos_portal", cuantos.getOrDefault(DebtEvent.Type.portal_entered, 0));
        datos.put("repactaciones", cuantos.getOrDefault(DebtEvent.Type.repacted, 0));
        datos.put("pagos", cuantos.getOrDefault(DebtEvent.Type.payment_applied, 0));
        datos.put("saldadas", cuantos.getOrDefault(DebtEvent.Type.settled, 0));
        datos.put("disputas", cuantos.getOrDefault(DebtEvent.Type.disputed, 0));
        datos.put("retiradas", cuantos.getOrDefault(DebtEvent.Type.withdrawn, 0));
        datos.put("recuperado_clp", enPesos.longValue());
        datos.put("recuperado_uf", enUf.stripTrailingZeros());
        return datos;
    }
}
