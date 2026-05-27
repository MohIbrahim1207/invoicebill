package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findAllByOrderByIdDesc();
    List<Invoice> findByClientIdOrderByIdDesc(Long id);
    List<Invoice> findByClient_Owner_IdOrderByIdDesc(Long ownerId);
    List<Invoice> findByClient_IdAndClient_Owner_IdOrderByIdDesc(Long clientId, Long ownerId);

    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.client c LEFT JOIN FETCH c.owner ORDER BY i.id DESC")
    List<Invoice> findAllWithClientOrderByIdDesc();

    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.client c LEFT JOIN FETCH c.owner WHERE i.id = :id")
    Optional<Invoice> findByIdWithClient(@Param("id") Long id);

    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.client c LEFT JOIN FETCH c.owner WHERE c.owner.id = :ownerId ORDER BY i.id DESC")
    List<Invoice> findByClientOwnerIdWithClientOrderByIdDesc(@Param("ownerId") Long ownerId);
}