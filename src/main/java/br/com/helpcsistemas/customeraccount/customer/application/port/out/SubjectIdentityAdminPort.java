package br.com.helpcsistemas.customeraccount.customer.application.port.out;

import java.util.UUID;

public interface SubjectIdentityAdminPort {

    void setCustomerId(String subject, String verifiedEmail, UUID customerId);
}
