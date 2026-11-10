package br.com.helpcsistemas.customeraccount.consent.application.port.out;

import br.com.helpcsistemas.customeraccount.consent.domain.event.ConsentEvent.ConsentGranted;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.List;
import java.util.UUID;

public interface ConsentProjection {
    void save(UUID customer, TenantId tenant, long version, java.time.Instant linkedAt, List<ConsentGranted> granted);

    void remove(UUID customer, TenantId tenant);

    void removeStale(UUID customer, TenantId tenant, java.time.Instant currentGeneration);

    void erase(UUID customer);
}
