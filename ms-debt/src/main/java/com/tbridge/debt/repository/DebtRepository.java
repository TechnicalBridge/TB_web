package com.tbridge.debt.repository;

import com.tbridge.debt.model.Debt;
import com.tbridge.debt.model.Batch;
import com.tbridge.debt.model.Campaign;
import com.tbridge.debt.model.Debtor;
import com.tbridge.debt.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DebtRepository extends JpaRepository<Debt, Long> {

    /**
     * La cartera que opera una organizacion en DataBridge: la suya como
     * acreedora y la que ella misma entrego como agencia.
     *
     * <p>En la cadena Patrimonio -> APOFYX -> DataBridge, quien trabaja aqui
     * es APOFYX. Patrimonio nunca entra a DataBridge (contrato, seccion 1), y
     * con solo "donde soy acreedor" el personal de APOFYX veia una cartera
     * vacia.
     *
     * <p>Nunca findAll(): el modelo anterior hacia eso y cualquier acreedor
     * veia las deudas de todos.
     */
    @Query("select d from Debt d where d.creditor = :org or d.lastBatch.sender = :org "
            + "order by d.updatedAt desc")
    List<Debt> carteraDe(@Param("org") Organization org);

    List<Debt> findByDebtorOrderByUpdatedAtDesc(Debtor debtor);

    List<Debt> findByCampaign(Campaign campaign);

    List<Debt> findByLastBatch(Batch lote);

    Optional<Debt> findByCreditorAndExternalId(Organization creditor, String externalId);
}
