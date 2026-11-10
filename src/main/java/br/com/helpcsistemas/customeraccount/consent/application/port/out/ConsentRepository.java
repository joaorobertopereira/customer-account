package br.com.helpcsistemas.customeraccount.consent.application.port.out;

import br.com.helpcsistemas.customeraccount.consent.domain.model.ConsentLedger;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.UUID;

public interface ConsentRepository {
    ConsentLedger load(UUID customerId, TenantId tenant);

    void save(ConsentLedger ledger);
}
