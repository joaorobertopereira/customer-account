package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider;

import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;

/** Credencial do provedor não cadastrada no cofre: tratada como falha técnica (aciona a contingência). */
public class ProviderCredentialsMissingException extends RuntimeException {

    public ProviderCredentialsMissingException(ProviderId provider, String detail) {
        super("Credencial do provedor " + provider + " não configurada: " + detail);
    }
}
