package br.com.helpcsistemas.customeraccount.customer.application.port.out;

import java.util.UUID;

public interface SubjectLinkCodeHasherPort {

    String hash(String challengeId, String subject, String tenantId, UUID customerId, String code);
}
