package br.com.helpcsistemas.customeraccount.customer.adapter.out.privacy;

import br.com.helpcsistemas.customeraccount.customer.application.port.out.PersonalDataErasurePort;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.privacy.CryptoShredder;
import org.springframework.stereotype.Component;

@Component
class CryptoShreddingErasureAdapter implements PersonalDataErasurePort {

    private final CryptoShredder shredder;

    CryptoShreddingErasureAdapter(CryptoShredder shredder) {
        this.shredder = shredder;
    }

    @Override
    public void destroyKeys(CustomerId id) {
        shredder.shred(id.toString());
    }
}
