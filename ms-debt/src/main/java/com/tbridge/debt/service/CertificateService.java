package com.tbridge.debt.service;

import com.tbridge.common.exception.ApiException;
import com.tbridge.common.jwt.JwtPrincipal;
import com.tbridge.debt.model.Debt;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** El certificado de deuda pagada. */
@Service
@Transactional(readOnly = true)
public class CertificateService {

    private final DebtService debts;

    public CertificateService(DebtService debts) {
        this.debts = debts;
    }

    public byte[] generate(JwtPrincipal user, Long debtId) {
        Debt debt = debts.requireVisible(user, debtId);
        //  Por estado, no solo por saldo: una deuda retirada tambien queda en
        //  cero (sus cuotas se anulan), y certificar que se PAGO algo que el
        //  acreedor retiro, o que se disputo, seria un documento falso.
        if (debt.getStatus() != Debt.Status.paid || debts.saldo(debt).compareTo(BigDecimal.ZERO) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "El certificado se emite solo para deudas pagadas por completo.");
        }
        try {
            return new DocumentoPdf()
                    .encabezado("Certificado de deuda pagada", "Folio " + debt.getId() + ", emitido el "
                            + DocumentoPdf.fecha(DocumentoPdf.hoy()))
                    .parrafo(debt.getDebtor().getFullName() + ", RUT " + DocumentoPdf.rut(debt.getDebtor().getRut())
                            + ", no tiene saldo pendiente con " + debt.getCreditor().getTradeName()
                            + " por la deuda que se detalla abajo.")
                    .destacado("Saldo", DocumentoPdf.dinero(BigDecimal.ZERO, debt.getCurrency()), null)
                    .datos(new String[][]{
                            {"Acreedor", debt.getCreditor().getTradeName() + ", RUT "
                                    + DocumentoPdf.rut(debt.getCreditor().getRut())},
                            {"Deuda", debt.getConcept()},
                            {"Referencia del acreedor", debt.getExternalId()},
                            {"Monto original", DocumentoPdf.dinero(debt.getOriginalAmount(), debt.getCurrency())},
                            {"Estado", "Pagada por completo"},
                    })
                    .pie("DataBridge emite este certificado con el registro de los pagos de la deuda. "
                            + debt.getCreditor().getTradeName() + " recibió el aviso de cada uno.")
                    .cerrar();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el certificado");
        }
    }
}
