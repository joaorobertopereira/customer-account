package br.com.helpcsistemas.customeraccount.customer.application.port.out;

import java.time.Duration;

public interface SubjectLinkOtpDeliveryPort {

    void send(String email, String code, Duration ttl);
}
