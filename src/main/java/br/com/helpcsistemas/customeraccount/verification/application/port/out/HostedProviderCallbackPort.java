package br.com.helpcsistemas.customeraccount.verification.application.port.out;

import br.com.helpcsistemas.customeraccount.verification.domain.model.HostedResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import java.util.Map;

/** Porta para autenticar e normalizar callbacks assinados dos provedores hospedados. */
public interface HostedProviderCallbackPort {

    ProviderId id();

    HostedResult parseCallback(byte[] body);

    boolean authenticateCallback(Map<String, String> headers, byte[] body, String tenantId);
}
