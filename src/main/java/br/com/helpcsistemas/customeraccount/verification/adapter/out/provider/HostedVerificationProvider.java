package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider;

import br.com.helpcsistemas.customeraccount.verification.application.port.out.HostedSession;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderRequest;
import java.net.URI;

/** SPI para provedores que hospedam a jornada do titular e retornam uma URL de sessão. */
public interface HostedVerificationProvider extends VerificationProvider {

    HostedSession start(ProviderRequest request, URI returnUrl);
}
