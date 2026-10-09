package com.tbridge.ai.service;

import com.tbridge.ai.dto.response.Animo;
import com.tbridge.ai.util.Valor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * El motor local del asistente: responde sin LLM, con reglas.
 *
 * <p>Lee las deudas tal como las entrega ms-debt ({@code saldo}, {@code moneda},
 * {@code estado}...). Pesos y UF no se suman entre si: una deuda en UF sumada a
 * una en pesos daria un total que no significa nada.
 *
 * <p>El analisis de sentimiento es por lexico, no un modelo. Alcanza para lo que
 * importa en cobranza: notar cuando alguien esta frustrado, o cuando desconfia
 * de que el mensaje sea real, que es la razon principal por la que la gente no
 * paga lo que si quiere pagar (docs de APOFYX, seccion 10).
 */
public final class MotorLocal {

    static final Set<String> VIGENTES = Set.of("open", "repacted");

    static final Map<String, String> ESTADOS = Map.of(
            "open", "pendiente",
            "repacted", "en convenio de pago",
            "paid", "pagada",
            "withdrawn", "retirada por el acreedor",
            "disputed", "en revision");

    //  Raices, sin tildes: el texto se normaliza antes de buscar.
    private static final List<String> FRUSTRACION = List.of(
            "harto", "chato", "cansad", "enojad", "molest", "rabia", "abuso", "injust", "reclam",
            "no puedo pagar", "no tengo plata", "no me alcanza", "cesante", "sin trabajo", "sin pega",
            "desesperad", "angusti", "me cobran de mas", "no es justo", "pesimo", "horrible");
    private static final List<String> DESCONFIANZA = List.of(
            "estafa", "fraude", "phishing", "es real", "es verdad", "es confiable", "confiar", "sospech",
            "quien eres", "quienes son", "de donde sacaron", "como tienen mis datos", "como consiguieron",
            "me estan cobrando algo que no", "no reconozco", "no es mia", "no es mio");
    private static final List<String> POSITIVO = List.of(
            "gracias", "genial", "perfecto", "excelente", "buenisimo", "bacan", "listo", "super");

    private static final Pattern SIN_PLATA =
            Pattern.compile("no puedo pagar|no tengo plata|no me alcanza|cesante|sin trabajo|sin pega");
    private static final Pattern SALUDO = Pattern.compile("\\b(hola|buenas|hey|saludos|alo)\\b");
    private static final Pattern CUOTAS = Pattern.compile("repact|cuot|plazo|convenio|plan");
    private static final Pattern PAGAR = Pattern.compile("pagar|pago|pasarela|khipu|webpay|mercado");
    private static final Pattern SALDO = Pattern.compile("saldo|deuda|cuanto|debo|a quien");

    private MotorLocal() {
    }

    static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFKD)
                .replaceAll("[^\\x00-\\x7F]", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("[\\s\\x1C-\\x1F]+", " ").strip();
    }

    /**
     * Frustracion y desconfianza pesan mas que lo positivo: "gracias, pero esto
     * es un abuso" es un reclamo, no un agradecimiento.
     */
    public static Animo sentimiento(String mensaje) {
        String texto = normalizar(mensaje);
        Map<String, List<String>> lexicos = new LinkedHashMap<>();
        lexicos.put("desconfianza", DESCONFIANZA);
        lexicos.put("frustracion", FRUSTRACION);
        lexicos.put("positivo", POSITIVO);
        for (Map.Entry<String, List<String>> lexico : lexicos.entrySet()) {
            int senales = (int) lexico.getValue().stream().filter(texto::contains).count();
            if (senales > 0) {
                return new Animo(lexico.getKey(), senales);
            }
        }
        return new Animo("neutral", 0);
    }

    public static String dinero(Object valor) {
        return dinero(valor, "CLP");
    }

    /** "$1.040.000" o "UF 1.234,50". */
    public static String dinero(Object valor, String moneda) {
        double n = numero(valor);
        if ("UF".equals(moneda)) {
            return "UF " + formato("#,##0.00").format(new BigDecimal(n).setScale(2, RoundingMode.HALF_EVEN));
        }
        return "$" + formato("#,##0").format(new BigDecimal(n).setScale(0, RoundingMode.HALF_EVEN));
    }

    /** El saldo vigente por moneda: "$1.040.000 y UF 115,50". */
    public static String totales(List<Map<String, Object>> deudas) {
        Map<String, Double> porMoneda = new LinkedHashMap<>();
        for (Map<String, Object> d : deudas) {
            if (vigente(d)) {
                porMoneda.merge(moneda(d), numero(d.get("saldo")), Double::sum);
            }
        }
        if (porMoneda.isEmpty()) {
            return dinero(0);
        }
        List<String> partes = new ArrayList<>();
        porMoneda.forEach((moneda, total) -> partes.add(dinero(total, moneda)));
        return String.join(" y ", partes);
    }

    private static String linea(Map<String, Object> d) {
        return "- " + d.get("acreedor") + " (" + d.get("concepto") + "): "
                + dinero(d.get("saldo"), Objects.toString(d.get("moneda"), null)) + ", " + estado(d);
    }

    /** Lo que responde el asistente sin LLM. */
    public static String responder(String mensaje, List<Map<String, Object>> deudas) {
        String texto = normalizar(mensaje);
        String animo = sentimiento(mensaje).etiqueta();
        List<Map<String, Object>> vigentes = deudas.stream().filter(d -> vigente(d)).toList();
        List<Map<String, Object>> pagadas = deudas.stream().filter(d -> "paid".equals(d.get("estado"))).toList();
        Set<String> acreedores = new TreeSet<>();
        deudas.stream().map(d -> d.get("acreedor")).filter(Valor::presente)
                .map(String::valueOf).forEach(acreedores::add);

        if (animo.equals("desconfianza")) {
            String quien = acreedores.isEmpty() ? "" : " Tu deuda es con " + String.join(", ", acreedores) + ".";
            return "Es bueno que lo preguntes. Technical Bridge nunca te pide contraseñas ni datos "
                    + "bancarios, y nunca te manda un enlace para entrar: entras escribiendo la dirección "
                    + "tú mismo, con tu RUT y tu código." + quien + " Si algo no te cuadra, puedes confirmarlo "
                    + "directamente con esa empresa antes de pagar. Si la deuda no te corresponde, díselo "
                    + "a ellos: no tienes que pagar algo que no reconoces.";
        }

        String prefacio = "";
        if (animo.equals("frustracion")) {
            prefacio = "Entiendo que es una situación difícil. ";
            if (SIN_PLATA.matcher(texto).find()) {
                return prefacio + "Puedes dividir lo que debes en 3 a 24 cuotas, sin interés: el total es "
                        + "lo mismo que debes hoy. En cada deuda está el botón Ver planes para simularlo sin "
                        + "comprometerte. Tu saldo vigente es " + totales(deudas) + ".";
            }
        }

        if (SALUDO.matcher(texto).find()) {
            return "Hola, soy el asistente de Technical Bridge. Te puedo decir cuánto debes y a quién, "
                    + "cómo pagar en cuotas y cómo pagar. ¿Qué necesitas?";
        }
        if (texto.contains("certificad")) {
            if (!pagadas.isEmpty()) {
                return "Tienes " + pagadas.size() + " deuda(s) pagada(s). En Mis pagos, junto a cada "
                        + "una, está el botón Certificado para descargar el PDF.";
            }
            return "El certificado se emite cuando una deuda queda pagada por completo.";
        }
        if (CUOTAS.matcher(texto).find()) {
            return prefacio + "Eliges entre 3 y 24 meses y ves la cuota antes de aceptar. No hay "
                    + "interés: el total es lo que debes hoy, y la última cuota absorbe el redondeo. En "
                    + "UF, cada cuota se paga al valor de la UF del día en que pagas.";
        }
        if (PAGAR.matcher(texto).find()) {
            return prefacio + "En cada deuda está el botón Pagar: eliges la próxima cuota o todo el "
                    + "saldo, y pagas con Webpay, Mercado Pago o Khipu. Tu saldo se actualiza solo "
                    + "unos segundos después, y la empresa queda avisada.";
        }
        if (SALDO.matcher(texto).find()) {
            if (deudas.isEmpty()) {
                return "No encuentro pagos pendientes a tu nombre.";
            }
            List<String> lineas = new ArrayList<>();
            lineas.add(prefacio + "Tienes " + vigentes.size() + " deuda(s) vigente(s), por " + totales(deudas)
                    + " en total.");
            vigentes.stream().limit(5).map(MotorLocal::linea).forEach(lineas::add);
            if (!pagadas.isEmpty()) {
                lineas.add("Además, " + pagadas.size() + " ya está(n) pagada(s).");
            }
            return String.join("\n", lineas);
        }
        if (animo.equals("positivo")) {
            return "Con gusto. Si necesitas algo más sobre tus pagos, pregúntame.";
        }
        if (!vigentes.isEmpty()) {
            return prefacio + "Tu saldo vigente es " + totales(deudas) + " en " + vigentes.size() + " deuda(s). "
                    + "Te puedo contar el detalle, cómo pagar en cuotas o cómo pagar.";
        }
        if (!deudas.isEmpty()) {
            return "No tienes saldo pendiente. Si pagaste todo, puedes descargar el certificado de cada deuda.";
        }
        return "Pregúntame cuánto debes, cómo pagar en cuotas o cómo pagar.";
    }

    /** Pendiente o en convenio: lo que todavia se debe. */
    static boolean vigente(Map<String, Object> d) {
        return d.get("estado") instanceof String estado && VIGENTES.contains(estado);
    }

    /** El estado en palabras: "pendiente", "en convenio de pago"... */
    static String estado(Map<String, Object> d) {
        Object estado = d.get("estado");
        return ESTADOS.getOrDefault(String.valueOf(estado), String.valueOf(estado));
    }

    /** La moneda de la deuda; sin moneda, pesos. */
    static String moneda(Map<String, Object> d) {
        return Valor.presente(d.get("moneda")) ? String.valueOf(d.get("moneda")) : "CLP";
    }

    /** Un monto como numero; lo que no es numero vale cero. */
    static double numero(Object valor) {
        double n;
        if (valor instanceof Number numero) {
            n = numero.doubleValue();
        } else if (valor instanceof Boolean si) {
            n = si ? 1 : 0;
        } else {
            try {
                n = Double.parseDouble(Objects.toString(valor, "").strip());
            } catch (NumberFormatException noEsNumero) {
                n = 0;
            }
        }
        return Double.isFinite(n) ? n : 0;
    }

    private static DecimalFormat formato(String patron) {
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols(Locale.ROOT);
        simbolos.setGroupingSeparator('.');
        simbolos.setDecimalSeparator(',');
        return new DecimalFormat(patron, simbolos);
    }
}
