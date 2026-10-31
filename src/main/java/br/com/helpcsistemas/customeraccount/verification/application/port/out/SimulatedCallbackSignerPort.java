package br.com.helpcsistemas.customeraccount.verification.application.port.out;

public interface SimulatedCallbackSignerPort {

    String signature(String timestamp, String body);
}
