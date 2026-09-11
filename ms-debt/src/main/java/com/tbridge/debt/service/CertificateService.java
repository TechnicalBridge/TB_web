package com.tbridge.debt.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfWriter;
import com.tbridge.common.jwt.JwtPrincipal;
import com.tbridge.common.web.ApiException;
import com.tbridge.debt.domain.Debt;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class CertificateService {

    private final DebtService debts;

    public CertificateService(DebtService debts) {
        this.debts = debts;
    }

    public byte[] generate(JwtPrincipal user, String debtId) {
        Debt debt = debts.requireVisible(user, debtId);
        if (debt.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Aún hay saldo pendiente. El certificado se emite con deuda cero.");
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.LETTER, 56, 56, 64, 56);
            PdfWriter.getInstance(document, out);
            document.open();

            Font kicker = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(16, 120, 150));
            Font title = new Font(Font.HELVETICA, 22, Font.BOLD, new Color(10, 22, 40));
            Font body = new Font(Font.HELVETICA, 12, Font.NORMAL, new Color(20, 32, 48));
            Font muted = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(90, 110, 130));

            Paragraph brand = new Paragraph("TECHNICAL BRIDGE", kicker);
            brand.setAlignment(Element.ALIGN_CENTER);
            document.add(brand);

            Paragraph heading = new Paragraph("Certificado de Deuda Cero", title);
            heading.setAlignment(Element.ALIGN_CENTER);
            heading.setSpacingBefore(12);
            heading.setSpacingAfter(18);
            document.add(heading);

            document.add(new Paragraph(
                    "Se certifica que " + debt.getDebtorName() + " (" + debt.getDebtorEmail() + ") no registra saldo pendiente "
                            + "con " + debt.getCreditorName() + " respecto de la obligación descrita a continuación.",
                    body
            ));
            document.add(new Paragraph(" ", body));
            document.add(new Paragraph("Acreedor: " + debt.getCreditorName(), body));
            document.add(new Paragraph("Descripción: " + (debt.getDescription() == null ? "—" : debt.getDescription()), body));
            document.add(new Paragraph("Monto original: " + formatClp(debt.getOriginalAmount()), body));
            document.add(new Paragraph("Saldo: " + formatClp(BigDecimal.ZERO), body));
            document.add(new Paragraph("Estado: PAGADA", body));
            document.add(new Paragraph("Folio: " + debt.getId(), muted));
            document.add(new Paragraph(" ", body));

            Paragraph date = new Paragraph(
                    "Emitido el " + LocalDate.now().format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CL"))),
                    muted
            );
            date.setSpacingBefore(24);
            document.add(date);

            Paragraph stamp = new Paragraph("Documento generado automáticamente · registro inmutable de auditoría", muted);
            stamp.setSpacingBefore(8);
            document.add(stamp);

            Paragraph sign = new Paragraph(new Phrase("Technical Bridge  ·  DataBridge", kicker));
            sign.setAlignment(Element.ALIGN_CENTER);
            sign.setSpacingBefore(36);
            document.add(sign);

            document.close();
            return out.toByteArray();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el certificado");
        }
    }

    private static String formatClp(BigDecimal amount) {
        return "$" + String.format(Locale.US, "%,.0f", amount).replace(',', '.');
    }
}
