package com.tbridge.debt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbridge.debt.client.AuthClient;
import com.tbridge.debt.dto.response.ContactosResponse;
import com.tbridge.debt.model.Campaign;
import com.tbridge.debt.model.Debt;
import com.tbridge.debt.model.DebtEvent;
import com.tbridge.debt.model.Debtor;
import com.tbridge.debt.repository.CampaignRepository;
import com.tbridge.debt.repository.DebtEventRepository;
import com.tbridge.debt.repository.DebtRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * La campana, ejecutada: APOFYX decide la estrategia (canales, intentos,
 * cadencia y fechas) y DataBridge la cumple.
 *
 * <p>Cada toque es el mismo correo de la invitacion: un codigo para entrar al
 * portal, sin el monto ni un enlace. El toque {@code k} de una deuda sale el
 * dia {@code entrada + cadencia[k]}, donde la entrada es cuando la deuda llego
 * a DataBridge (o el inicio de la campana, si empezo despues). Sale mientras:
 *
 * <ul>
 *   <li>la campana este en curso, entre su inicio y su fin, y contacte por correo;</li>
 *   <li>la deuda siga abierta: si se paga, se repacta, se reclama o se retira,
 *       la campana la deja. El convenio tiene sus propios recordatorios;</li>
 *   <li>no se hayan hecho todos los intentos;</li>
 *   <li>la ley lo permita: de lunes a sabado, de 8:00 a 20:00, nunca un feriado,
 *       y no mas de lo que deja {@link LimiteDeContacto}. Un toque que la ley
 *       no deja hoy sale en la proxima pasada en que si.</li>
 * </ul>
 */
@Service
public class ContactoService {

    private static final Logger log = LoggerFactory.getLogger(ContactoService.class);
    private static final ZoneId CHILE = ZoneId.of("America/Santiago");

    /** La de APOFYX (su documento, seccion 8): para una campana que no trae la suya. */
    static final List<Integer> CADENCIA_POR_OMISION = List.of(1, 4, 11, 25, 45);

    private static final LocalTime DESDE = LocalTime.of(8, 0);
    private static final LocalTime HASTA = LocalTime.of(20, 0);

    private final CampaignRepository campaigns;
    private final DebtRepository debts;
    private final DebtEventRepository events;
    private final AuthClient auth;
    private final LimiteDeContacto limite;
    private final ObjectMapper json;
    private final Set<LocalDate> feriados;
    /** El ultimo dia en que se reviso si faltan feriados: se avisa una vez al dia, no cada 15 minutos. */
    private LocalDate feriadosRevisados;

    public ContactoService(CampaignRepository campaigns, DebtRepository debts, DebtEventRepository events,
                           AuthClient auth, LimiteDeContacto limite, ObjectMapper json,
                           @Value("${app.contacto.feriados:}") String feriados) {
        this.campaigns = campaigns;
        this.debts = debts;
        this.events = events;
        this.auth = auth;
        this.limite = limite;
        this.json = json;
        this.feriados = Arrays.stream(feriados.split(","))
                .map(String::trim)
                .filter(dia -> !dia.isEmpty())
                .map(LocalDate::parse)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Scheduled(cron = "${app.contacto.cron:0 */15 * * * *}", zone = "America/Santiago")
    public void pasar() {
        LocalDateTime ahora = LocalDateTime.now(CHILE);
        if (!ahora.toLocalDate().equals(feriadosRevisados)) {
            feriadosRevisados = ahora.toLocalDate();
            List<Integer> faltan = anosSinFeriados(feriadosRevisados);
            if (!faltan.isEmpty()) {
                log.warn("No hay feriados cargados para {}: las campanas contactarian en feriados. "
                        + "Agregalos en app.contacto.feriados o en CONTACTO_FERIADOS", faltan);
            }
        }
        ContactosResponse hecho = contactar(ahora, null);
        if (hecho.enviados() > 0) {
            log.info("Campanas: {} toque(s) enviados, {} pendientes", hecho.enviados(), hecho.omitidos());
        }
    }

    /**
     * Los anos sin ningun feriado cargado: este, y desde diciembre tambien el
     * siguiente, para que haya un mes para cargarlos antes de que haga falta.
     */
    List<Integer> anosSinFeriados(LocalDate hoy) {
        List<Integer> anos = hoy.getMonthValue() == 12
                ? List.of(hoy.getYear(), hoy.getYear() + 1)
                : List.of(hoy.getYear());
        return anos.stream()
                .filter(ano -> feriados.stream().noneMatch(dia -> dia.getYear() == ano))
                .toList();
    }

    /** Si a esta hora de Chile se puede hacer cobranza: lunes a sabado, de 8:00 a 20:00, sin feriados. */
    public boolean horaDeContacto(LocalDateTime ahora) {
        LocalTime hora = ahora.toLocalTime();
        return ahora.getDayOfWeek() != DayOfWeek.SUNDAY
                && !feriados.contains(ahora.toLocalDate())
                && !hora.isBefore(DESDE) && hora.isBefore(HASTA);
    }

    /**
     * Una pasada: los toques que tocan a esta hora de Chile. Con otra hora que
     * la de ahora sirve para probar una cadencia sin esperar semanas: los
     * toques quedan fechados a esa hora. Con {@code soloCampana} (su id
     * externo) pasa solo por esa, y las demas no se enteran.
     */
    @Transactional
    public ContactosResponse contactar(LocalDateTime ahora, String soloCampana) {
        if (!horaDeContacto(ahora)) {
            return new ContactosResponse(0, 0, false);
        }
        Instant cuando = ahora.atZone(CHILE).toInstant();
        LocalDate hoy = ahora.toLocalDate();
        int enviados = 0;
        int omitidos = 0;

        for (Campaign campana : campaigns.findByStatus(Campaign.Status.running)) {
            if ((soloCampana != null && !soloCampana.equals(campana.getExternalId()))
                    || hoy.isBefore(campana.getStartsOn())
                    || (campana.getEndsOn() != null && hoy.isAfter(campana.getEndsOn()))
                    || !porCorreo(campana)) {
                continue;
            }
            List<Integer> cadencia = cadencia(campana);
            int tope = Math.min(campana.getAttempts(), cadencia.size());
            List<Debt> abiertas = debts.findByCampaign(campana).stream()
                    .filter(d -> d.getStatus() == Debt.Status.open)
                    .toList();
            if (abiertas.isEmpty()) {
                continue;
            }
            Map<Long, Integer> hechos = toquesHechos(campana, abiertas);

            for (Debt deuda : abiertas) {
                int toque = hechos.getOrDefault(deuda.getId(), 0);
                if (toque >= tope) {
                    continue;
                }
                LocalDate entrada = LocalDate.ofInstant(deuda.getCreatedAt(), CHILE);
                if (entrada.isBefore(campana.getStartsOn())) {
                    entrada = campana.getStartsOn();
                }
                if (hoy.isBefore(entrada.plusDays(cadencia.get(toque)))) {
                    continue;
                }
                Debtor deudor = deuda.getDebtor();
                if (deudor.getEmail() == null || deudor.getEmail().isBlank() || !limite.permite(deudor, cuando)) {
                    omitidos++;
                    continue;
                }
                try {
                    auth.emitirCodigo(new AuthClient.PedidoDeCodigo(deudor.getRut(), List.of("correo"),
                            deudor.getEmail(), deuda.getCreditor().getTradeName(), deuda.getExternalId()));
                } catch (RuntimeException e) {
                    log.warn("No salio el toque {} de la deuda {}: {}", toque + 1, deuda.getId(), e.getMessage());
                    omitidos++;
                    continue;
                }
                DebtEvent enviado = DebtEvent.de(deuda, DebtEvent.Type.code_sent, DebtEvent.Actor.system)
                        .conReferencia("campana " + campana.getName() + ", toque " + (toque + 1) + " de " + tope)
                        .conDetalle("{\"campana\":" + campana.getId() + ",\"toque\":" + (toque + 1) + "}");
                enviado.setOccurredAt(cuando);
                events.save(enviado);
                enviados++;
            }
        }
        return new ContactosResponse(enviados, omitidos, true);
    }

    /** Hoy DataBridge contacta solo por correo: WhatsApp no esta conectado. */
    private static boolean porCorreo(Campaign campana) {
        return campana.getChannels() != null && campana.getChannels().contains("\"correo\"");
    }

    /** Los dias de la cadencia, de menor a mayor. Sin una valida, la de APOFYX. */
    private List<Integer> cadencia(Campaign campana) {
        if (campana.getCadenceDays() == null || campana.getCadenceDays().isBlank()) {
            return CADENCIA_POR_OMISION;
        }
        try {
            List<Integer> dias = new ArrayList<>();
            for (JsonNode dia : json.readTree(campana.getCadenceDays())) {
                if (dia.canConvertToInt() && dia.asInt() >= 0) {
                    dias.add(dia.asInt());
                }
            }
            return dias.isEmpty() ? CADENCIA_POR_OMISION : dias.stream().sorted().toList();
        } catch (Exception e) {
            return CADENCIA_POR_OMISION;
        }
    }

    /** Cuantos toques de esta campana lleva cada deuda: los code_sent que la nombran. */
    private Map<Long, Integer> toquesHechos(Campaign campana, List<Debt> deudas) {
        Map<Long, Integer> hechos = new HashMap<>();
        for (DebtEvent evento : events.findByDebtInAndType(deudas, DebtEvent.Type.code_sent)) {
            if (evento.getDetail() == null) {
                continue;
            }
            try {
                if (json.readTree(evento.getDetail()).path("campana").asLong(-1) == campana.getId()) {
                    hechos.merge(evento.getDebt().getId(), 1, Integer::sum);
                }
            } catch (Exception ilegible) {
                //  Un detalle que no se puede leer no es un toque de campana.
            }
        }
        return hechos;
    }
}
