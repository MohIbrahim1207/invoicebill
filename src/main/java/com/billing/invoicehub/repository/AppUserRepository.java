package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findFirstByUsername(String username);

    Optional<AppUser> findFirstByUsernameAndUserDomain(String username, String userDomain);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findFirstByEmailIgnoreCase(String email);

    Optional<AppUser> findFirstByEmailIgnoreCaseAndUserDomain(String email, String userDomain);

    Optional<AppUser> findFirstByUsernameAndEmailIgnoreCase(String username, String email);

    Optional<AppUser> findFirstByUsernameAndEmailIgnoreCaseAndUserDomain(String username, String email, String userDomain);

    List<AppUser> findAllByEmailIgnoreCase(String email);

    List<AppUser> findAllByUsername(String username);

    Optional<AppUser> findByVendorCode(String vendorCode);

    Optional<AppUser> findFirstByVendorCode(String vendorCode);

    Optional<AppUser> findByCompanyNameIgnoreCase(String companyName);

    Optional<AppUser> findFirstByCompanyNameIgnoreCase(String companyName);

    Optional<AppUser> findByGstNumber(String gstNumber);

    Optional<AppUser> findFirstByGstNumber(String gstNumber);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndUserDomain(String email, String userDomain);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndUserDomain(String username, String userDomain);

    long countByVendorCodeStartingWith(String prefix);

    @Query(value = "select distinct u from AppUser u join u.roles r where r.name = :roleName order by u.id desc")
    List<AppUser> findByRoles_NameOrderByIdDesc(@Param(value = "roleName") String roleName);

    @Query(value = "select distinct u from AppUser u join u.roles r where r.name = :roleName and u.enabled = true order by u.id desc")
    List<AppUser> findByRoleNameAndEnabledTrue(@Param(value = "roleName") String roleName);

    List<AppUser> findAllByOrderByIdDesc();
}