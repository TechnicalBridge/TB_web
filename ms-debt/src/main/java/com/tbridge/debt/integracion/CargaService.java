package com.tbridge.debt.integracion;

import com.tbridge.debt.domain.Batch;
import com.tbridge.debt.domain.Campaign;
import com.tbridge.debt.domain.Mandate;
import com.tbridge.debt.domain.Organization;
import com.tbridge.debt.repo.CampaignRepository;
import com.tbridge.debt.repo.MandateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * La carga de cartera por archivo, desde el portal: el "modo archivo" del
 * contrato (seccion 9) para quien no tiene integracion por API.
 *
 * <p>El archivo no tiene una ingesta propia. Se convierte a Cartera v1 y entra
 * por {@link CarteraIntakeService}, igual que lo que llega por API.
 */
@Service
@Transactional(readOnly = true)
public class CargaService {

    private final CarteraIntakeService ingesta;
    private final MandateRepository mandatos;
    private final CampaignRepository campanas;

    public CargaService(CarteraIntakeService ingesta, MandateRepository mandatos, CampaignRepository campanas) {
        this.ingesta = ingesta;
        this.mandatos = mandatos;
        this.campanas = campanas;
    }

    /**
     * Por cuenta de quien puede cargar cartera una organizacion: la propia, si
     * es acreedora, y la de cada acreedor que le dio un mandato vigente, con
     * sus campanas. Es lo que la pantalla ofrece para elegir, en vez de pedir
     * que se escriba un RUT.
     */
    public Map<String, Object> opciones(Organization organizacion) {
        List<Map<String, Object>> acreedores = new ArrayList<>();
        if (organizacion.getKind() != Organization.Kind.agency) {
            acreedores.add(acreedor(organizacion, List.of()));
        }
        LocalDate hoy = LocalDate.now();
        for (Mandate mandato : mandatos.findByAgencyAndStatus(organizacion, Mandate.Status.active)) {
            if (mandato.vigenteAl(hoy)) {
                acreedores.add(acreedor(mandato.getCreditor(),
                        campanas.findByAgencyAndCreditorOrderByStartsOnDesc(organizacion, mandato.getCreditor())));
            }
        }
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("organizacion", organizacion.getTradeName());
        respuesta.put("acreedores", acreedores);
        return respuesta;
    }

    @Transactional
    public Map<String, Object> subir(Organization organizacion, byte[] archivo, String loteId,
                                     String fechaCorte, String acreedorRut, String campana) {
        boolean propia = organizacion.getRut().equalsIgnoreCase(acreedorRut);
        CarteraCsv.Lote lote = new CarteraCsv.Lote(loteId, fechaCorte, acreedorRut,
                propia ? null : organizacion.getRut(), propia ? null : campana);
        return ingesta.recibir(organizacion, CarteraCsv.leer(archivo, lote), Batch.Source.file);
    }

    private static Map<String, Object> acreedor(Organization org, List<Campaign> suyas) {
        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("rut", org.getRut());
        fila.put("nombre", org.getTradeName());
        fila.put("campanas", suyas.stream().map(c -> Map.of("idExterno", c.getExternalId(), "nombre", c.getName())).toList());
        return fila;
    }
}
