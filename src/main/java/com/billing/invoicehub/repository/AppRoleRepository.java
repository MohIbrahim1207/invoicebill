package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.AppRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppRoleRepository extends JpaRepository<AppRole, Long> {

    Optional<AppRole> findByName(String name);

    Optional<AppRole> findFirstByName(String name);

    boolean existsByName(String name);
}
