package br.com.helpcsistemas.customeraccount.verification.application.port.out;

import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.util.List;

/** Acesso aos provedores externos. A escolha do provedor é sempre do chamador (política do tenant). */
public interface VerificationProviderGateway {

    /** @throws ProviderUnavailableException falha técnica (timeout, erro, circuito aberto): aciona a contingência */
    ProviderResult verify(ProviderId provider, ProviderRequest request);

    /**
     * Checa a resposta do titular a um desafio emitido pelo provedor.
     *
     * @throws ProviderUnavailableException falha técnica (não há contingência: o desafio pertence a este provedor)
     */
    ProviderResult confirm(ProviderId provider, ProviderRequest request, String transactionId, String answer);

    boolean supports(ProviderId provider, VerificationType type);

    List<ProviderDescriptor> providers();
}
