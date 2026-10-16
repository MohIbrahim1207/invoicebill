package com.billing.invoicehub.entity;

/**
 * Defines the distinct authentication domains in InvoiceHub.
 * VENDOR: External vendor portal users.
 * INTERNAL: Internal company users (Employee, Admin, Accounts, Super Admin).
 */
public enum UserDomain {
    VENDOR,
    INTERNAL
}
