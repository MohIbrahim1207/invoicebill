package com.billing.invoicehub.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;

@Entity
public class VendorTicketHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private VendorTicket ticket;
    @Enumerated(EnumType.STRING)
    private TicketStatus status;
    private LocalDateTime changedAt;
    private String comment;

    public VendorTicketHistory() {}

    public VendorTicketHistory(VendorTicket ticket, TicketStatus status, LocalDateTime changedAt, String comment) {
        this.ticket = ticket;
        this.status = status;
        this.changedAt = changedAt;
        this.comment = comment;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public VendorTicket getTicket() { return ticket; }
    public void setTicket(VendorTicket ticket) { this.ticket = ticket; }
    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }
    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}

