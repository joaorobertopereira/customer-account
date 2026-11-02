package br.com.helpcsistemas.customeraccount.customer.adapter.out.persistence;

import br.com.helpcsistemas.customeraccount.customer.application.port.out.SubjectLinkCodeHasherPort;
import br.com.helpcsistemas.customeraccount.privacy.BlindIndex;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class HmacSubjectLinkCodeHasherAdapter implements SubjectLinkCodeHasherPort {

    private final BlindIndex blindIndex;

    HmacSubjectLinkCodeHasherAdapter(BlindIndex blindIndex) {
        this.blindIndex = blindIndex;
    }

    @Override
    public String hash(String challengeId, String subject, String tenantId, UUID customerId, String code) {
        return blindIndex.compute(
                "subject-link-otp", challengeId + ":" + subject + ":" + tenantId + ":" + customerId + ":" + code);
    }
}
