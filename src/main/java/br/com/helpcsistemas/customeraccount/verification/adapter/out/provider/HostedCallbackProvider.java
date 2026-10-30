package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider;

import br.com.helpcsistemas.customeraccount.verification.application.port.out.HostedProviderCallbackPort;

/** Provedor hospedado que valida a autenticidade e interpreta callbacks recebidos. */
public interface HostedCallbackProvider extends HostedVerificationProvider, HostedProviderCallbackPort {}
