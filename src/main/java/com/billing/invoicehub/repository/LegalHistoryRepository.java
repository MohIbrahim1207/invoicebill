package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.LegalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LegalHistoryRepository extends JpaRepository<LegalHistory, Long> {

    List<LegalHistory> findAllBySlugOrderByCreatedAtDesc(String slug);
}
