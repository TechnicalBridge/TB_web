package com.tbridge.auth.repo;

import com.tbridge.auth.domain.AccessCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccessCodeRepository extends JpaRepository<AccessCode, Long> {

    /**
     * El ultimo codigo sin usar de ese RUT.
     *
     * Se busca por RUT y no por la huella del codigo porque hace falta poder
     * contar los intentos fallidos: si se buscara por huella, un codigo
     * equivocado simplemente no encontraria fila y probar al azar saldria
     * gratis.
     */
    Optional<AccessCode> findFirstByDebtorRutAndConsumedAtIsNullOrderByIssuedAtDesc(String debtorRut);
}
