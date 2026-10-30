package br.com.helpcsistemas.customeraccount.verification.application.port.in;

import br.com.helpcsistemas.customeraccount.verification.domain.model.HostedResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;

public interface ReceiveProviderResultUseCase {

    void receive(ProviderId provider, HostedResult result);
}
