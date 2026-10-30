package br.com.helpcsistemas.customeraccount.customer.adapter.out.readmodel;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;

/**
 * Documento do read model. Campos em claro são apenas não pessoais ou pseudônimos (índices cegos); o perfil inteiro
 * fica cifrado com a chave do titular — o crypto-shredding também o torna ilegível (inclusive em backups).
 */
record CustomerDocument(
        @Id String id,
        long version,
        String status,
        String cpfIndex,
        String emailIndex,
        String mobilePhoneIndex,
        List<String> tenants,
        boolean emailVerified,
        boolean mobilePhoneVerified,
        Instant createdAt,
        Instant updatedAt,
        byte[] encryptedProfile) {}
