package com.tbridge.debt.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.tbridge.common.util.Rut;
import com.tbridge.debt.domain.Campaign;
import com.tbridge.debt.domain.Mandate;
import com.tbridge.debt.domain.Organization;
import com.tbridge.debt.repo.CampaignRepository;
import com.tbridge.debt.repo.MandateRepository;
import com.tbridge.debt.repo.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contrato 2: el mandato y la campana.
 *
 * <p>Lo que la agencia le dice a DataBridge antes de pasarle una cartera. No
 * lleva ningun dato personal: es quien cobra por cuenta de quien, y con que
 * estrategia.
 */
@Service
public class MandatoService {

    private final OrganizationRepository organizations;
    private final MandateRepository mandates;
    private final CampaignRepository campaigns;

    public MandatoService(OrganizationRepository organizations, MandateRepository mandates,
                          CampaignRepository campaigns) {
        this.organizations = organizations;
        this.mandates = mandates;
        this.campaigns = campaigns;
    }

    /**
     * La agencia declara que cobra por cuenta de un acreedor.
     *
     * <p><b>La agencia responde por el mandato:</b> es su contrato con el
     * acreedor. DataBridge registra quien lo declaro y cuando, que es lo que
     * permite reconstruir despues con que autorizacion se cobro.
     */
    @Transactional
    public Map<String, Object> registrarMandato(Organization agencia, JsonNode cuerpo) {
        if (!agencia.esAgencia()) {
            throw new CarteraInvalida("no_es_agencia",
                    "Esa organizacion no esta registrada como agencia de cobranza", 403);
        }
        Organization acreedor = buscarAcreedor(cuerpo.get("acreedor_rut"));
        if (acreedor.getId().equals(agencia.getId())) {
            throw new CarteraInvalida("mandato_invalido",
                    "Una agencia no puede tener mandato sobre si misma");
        }

        LocalDate desde = fecha(cuerpo.get("vigente_desde"), LocalDate.now());

        //  Declarar dos veces el mismo mandato no es un error: es un reintento.
        //  Se devuelve el que ya existe en vez de chocar contra el indice
        //  unico, que es lo que pasaba antes y dejaba a la agencia sin poder
        //  reintentar un registro que quizas si se habia guardado.
        Mandate existente = mandates
                .findByAgencyAndCreditorAndStatus(agencia, acreedor, Mandate.Status.active).stream()
                .filter(m -> m.getValidFrom().equals(desde))
                .findFirst()
                .orElse(null);
        if (existente != null) {
            return describir(existente);
        }

        Mandate mandato = new Mandate();
        mandato.setAgency(agencia);
        mandato.setCreditor(acreedor);
        mandato.setValidFrom(desde);
        if (cuerpo.hasNonNull("vigente_hasta")) {
            mandato.setValidTo(fecha(cuerpo.get("vigente_hasta"), null));
        }
        if (cuerpo.hasNonNull("mora_maxima_dias")) {
            mandato.setMaxOverdueDays((short) cuerpo.get("mora_maxima_dias").asInt(120));
        }
        mandato.setDeclaredBy(agencia.getTradeName());
        mandates.save(mandato);
        return describir(mandato);
    }

    private Map<String, Object> describir(Mandate mandato) {
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("id", mandato.getId());
        respuesta.put("acreedor_rut", mandato.getCreditor().getRut());
        respuesta.put("vigente_desde", mandato.getValidFrom());
        respuesta.put("mora_maxima_dias", mandato.getMaxOverdueDays());
        return respuesta;
    }

    @Transactional
    public Map<String, Object> registrarCampana(Organization agencia, JsonNode cuerpo) {
        Organization acreedor = buscarAcreedor(cuerpo.get("acreedor_rut"));
        String idExterno = texto(cuerpo.get("id_externo"));
        if (idExterno == null || idExterno.isBlank()) {
            throw new CarteraInvalida("campana_incompleta", "La campana necesita id_externo");
        }
        if (mandates.findByAgencyAndCreditorAndStatus(agencia, acreedor, Mandate.Status.active)
                .isEmpty()) {
            throw new CarteraInvalida("sin_mandato",
                    "No hay mandato vigente sobre ese acreedor", 403);
        }

        Campaign campana = campaigns.findByAgencyAndExternalId(agencia, idExterno)
                .orElseGet(Campaign::new);
        campana.setAgency(agencia);
        campana.setCreditor(acreedor);
        campana.setExternalId(idExterno);
        campana.setName(texto(cuerpo.get("nombre")) == null ? idExterno : texto(cuerpo.get("nombre")));
        campana.setStartsOn(fecha(cuerpo.get("inicio"), LocalDate.now()));
        if (cuerpo.hasNonNull("fin")) {
            campana.setEndsOn(fecha(cuerpo.get("fin"), null));
        }
        if (cuerpo.hasNonNull("canales")) {
            campana.setChannels(cuerpo.get("canales").toString());
        }
        if (cuerpo.hasNonNull("intentos")) {
            campana.setAttempts((short) cuerpo.get("intentos").asInt(3));
        }
        if (cuerpo.hasNonNull("cadencia_dias")) {
            campana.setCadenceDays(cuerpo.get("cadencia_dias").toString());
        }
        campaigns.save(campana);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("id", campana.getId());
        respuesta.put("id_externo", campana.getExternalId());
        respuesta.put("acreedor_rut", acreedor.getRut());
        respuesta.put("canales", campana.getChannels());
        return respuesta;
    }

    private Organization buscarAcreedor(JsonNode nodo) {
        String rut = Rut.normalizar(texto(nodo));
        if (!Rut.esValido(rut)) {
            throw new CarteraInvalida("acreedor_invalido", "El RUT del acreedor no es valido");
        }
        return organizations.findByRut(rut).orElseThrow(() -> new CarteraInvalida(
                "acreedor_desconocido", "El acreedor " + rut + " no esta registrado", 404));
    }

    private static String texto(JsonNode nodo) {
        return nodo == null || nodo.isNull() ? null : nodo.asText();
    }

    private static LocalDate fecha(JsonNode nodo, LocalDate porDefecto) {
        try {
            return LocalDate.parse(texto(nodo));
        } catch (Exception e) {
            return porDefecto;
        }
    }
}
