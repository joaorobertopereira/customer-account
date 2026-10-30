package br.com.helpcsistemas.customeraccount;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CustomerAccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerAccountApplication.class, args);
    }
}
