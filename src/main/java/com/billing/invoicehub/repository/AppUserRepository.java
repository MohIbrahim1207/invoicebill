package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    default Optional<AppUser> findByUsername(String username) {
        return findFirstByUsername(username);
    }

    Optional<AppUser> findFirstByUsername(String username);

    Optional<AppUser> findFirstByUsernameAndUserDomain(String username, String userDomain);

    default Optional<AppUser> findByEmailIgnoreCase(String email) {
        return findFirstByEmailIgnoreCase(email);
    }

    Optional<AppUser> findFirstByEmailIgnoreCase(String email);

    Optional<AppUser> findFirstByEmailIgnoreCaseAndUserDomain(String email, String userDomain);

    Optional<AppUser> findFirstByUsernameAndEmailIgnoreCase(String username, String email);

    Optional<AppUser> findFirstByUsernameAndEmailIgnoreCaseAndUserDomain(String username, String email, String userDomain);

    List<AppUser> findAllByEmailIgnoreCase(String email);

    List<AppUser> findAllByUsername(String username);

    Optional<AppUser> findByVendorCode(String vendorCode);

    Optional<AppUser> findFirstByVendorCode(String vendorCode);

    default Optional<AppUser> findByCompanyNameIgnoreCase(String companyName) {
        return findFirstByCompanyNameIgnoreCase(companyName);
    }

    Optional<AppUser> findFirstByCompanyNameIgnoreCase(String companyName);

    default Optional<AppUser> findByGstNumber(String gstNumber) {
        return findFirstByGstNumberIgnoreCase(gstNumber);
    }

    Optional<AppUser> findFirstByGstNumber(String gstNumber);

    Optional<AppUser> findFirstByGstNumberIgnoreCase(String gstNumber);

    boolean existsByGstNumberIgnoreCase(String gstNumber);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndUserDomain(String email, String userDomain);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndUserDomain(String username, String userDomain);

    long countByVendorCodeStartingWith(String prefix);

    @Query(value = "select distinct u from AppUser u join u.roles r where r.name = :roleName order by u.id desc")
    List<AppUser> findByRoles_NameOrderByIdDesc(@Param(value = "roleName") String roleName);

    @Query(value = "select distinct u from AppUser u join u.roles r where r.name = :roleName and u.enabled = true order by u.id desc")
    List<AppUser> findByRoleNameAndEnabledTrue(@Param(value = "roleName") String roleName);

    @Query("SELECT COUNT(DISTINCT u) FROM AppUser u JOIN u.roles r WHERE r.name = 'ROLE_VENDOR' AND (u.verified = false OR u.verified IS NULL) AND (u.rejectionReason IS NULL OR TRIM(u.rejectionReason) = '')")
    long countUnverifiedVendors();

    List<AppUser> findAllByOrderByIdDesc();
}
