package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository
        extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByVendorCode(String vendorCode);

    long countByVendorCodeStartingWith(String prefix);

    @Query(value="select distinct u from AppUser u join u.roles r where r.name = :roleName order by u.id desc")
    List<AppUser> findByRoles_NameOrderByIdDesc(@Param(value="roleName") String roleName);

    // Added for password reset — replaces findAll() scan
    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByCompanyNameIgnoreCase(String companyName);

    Optional<AppUser> findByGstNumber(String gstNumber);
}