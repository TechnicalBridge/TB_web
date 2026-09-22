package com.tbridge.auth.repo;

import com.tbridge.auth.domain.StaffUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaffUserRepository extends JpaRepository<StaffUser, Long> {

    Optional<StaffUser> findByEmailIgnoreCase(String email);
}
