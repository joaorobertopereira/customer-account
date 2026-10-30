package br.com.helpcsistemas.customeraccount.customer.domain.model;

public enum CustomerStatus {
    ACTIVE,
    /** Eliminação solicitada (LGPD art. 18, VI): dados serão destruídos via crypto-shredding. */
    ERASED
}
