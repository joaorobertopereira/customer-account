package br.com.helpcsistemas.customeraccount;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Falha o build se algum módulo acessar internals de outro ou se houver ciclo entre módulos. */
class ModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(CustomerAccountApplication.class);

    @Test
    void verifiesModularStructure() {
        MODULES.verify();
    }

    @Test
    void writesModuleDocumentation() {
        // Gera diagramas C4/PlantUML e o Application Module Canvas em target/spring-modulith-docs
        new Documenter(MODULES).writeDocumentation();
    }
}
