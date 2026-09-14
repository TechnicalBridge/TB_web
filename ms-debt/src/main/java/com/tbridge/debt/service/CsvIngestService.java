package com.tbridge.debt.service;

import com.tbridge.common.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CsvIngestService {

    private static final DateTimeFormatter[] DATES = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
    };

    private final DebtService debts;

    public CsvIngestService(DebtService debts) {
        this.debts = debts;
    }

    public Map<String, Object> ingest(MultipartFile file, String defaultCreditor) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Adjunta un archivo CSV");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".csv")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Solo se aceptan archivos .csv");
        }
        List<Map<String, Object>> imported = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header != null && header.startsWith("\uFEFF")) {
                header = header.substring(1);
            }
            if (header == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "El CSV está vacío");
            }
            int line = 1;
            String raw;
            while ((raw = reader.readLine()) != null) {
                line++;
                if (raw.isBlank()) {
                    continue;
                }
                String[] cols = raw.split(";", -1);
                if (cols.length < 4) {
                    cols = raw.split(",", -1);
                }
                if (cols.length < 4) {
                    errors.add("Línea " + line + ": se esperaban email,nombre,acreedor,monto[,fecha,descripcion]");
                    continue;
                }
                try {
                    String email = cols[0].trim().toLowerCase(Locale.ROOT);
                    String debtorName = cols[1].trim();
                    String creditor = cols[2].trim().isEmpty() ? defaultCreditor : cols[2].trim();
                    String digits = cols[3].trim().replace(".", "").replace(",", "").replaceAll("[^0-9]", "");
                    if (digits.isEmpty()) {
                        errors.add("Línea " + line + ": monto inválido");
                        continue;
                    }
                    BigDecimal amount = new BigDecimal(digits);
                    LocalDate due = cols.length > 4 ? parseDate(cols[4].trim()) : LocalDate.now().plusMonths(1);
                    String description = cols.length > 5 ? cols[5].trim() : "Carga CSV";
                    if (!email.contains("@") || amount.compareTo(BigDecimal.ZERO) <= 0) {
                        errors.add("Línea " + line + ": email o monto inválido");
                        continue;
                    }
                    var debt = debts.createDebt(email, debtorName.isEmpty() ? email : debtorName, creditor, amount, due, description);
                    imported.add(debts.toSummary(debt));
                } catch (Exception e) {
                    errors.add("Línea " + line + ": " + e.getMessage());
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No se pudo leer el CSV");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported.size());
        result.put("errors", errors);
        result.put("debts", imported);
        return result;
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now().plusMonths(1);
        }
        for (DateTimeFormatter formatter : DATES) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return LocalDate.now().plusMonths(1);
    }
}
