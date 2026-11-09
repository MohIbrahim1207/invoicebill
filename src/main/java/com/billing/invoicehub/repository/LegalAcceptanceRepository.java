package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.LegalAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LegalAcceptanceRepository extends JpaRepository<LegalAcceptance, Long> {

    List<LegalAcceptance> findAllByUsernameOrderByAcceptedAtDesc(String username);

    boolean existsByUsernameAndTermsVersionAndPrivacyVersion(String username, String termsVersion, String privacyVersion);
}
