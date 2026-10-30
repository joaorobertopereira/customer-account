package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider;

import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderRequest;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.util.Set;

/**
 * SPI de integração (camada anticorrupção): cada empresa (Twilio, ZeroBounce, OTP próprio...) é um bean que traduz o
 * protocolo dela para {@link ProviderResult}. Exceções = falha técnica (acionam a contingência).
 */
public interface VerificationProvider {

    ProviderId id();

    Set<VerificationType> supportedTypes();

    ProviderResult verify(ProviderRequest request);

    /** Checa a resposta do titular a um desafio emitido por {@link #verify} ({@code CHALLENGE_SENT}). */
    default ProviderResult confirm(ProviderRequest request, String transactionId, String answer) {
        throw new UnsupportedOperationException("Provedor " + id() + " não emite desafios");
    }
}
