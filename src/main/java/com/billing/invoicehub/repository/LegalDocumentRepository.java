package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.LegalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LegalDocumentRepository extends JpaRepository<LegalDocument, Long> {

    Optional<LegalDocument> findBySlug(String slug);
}
