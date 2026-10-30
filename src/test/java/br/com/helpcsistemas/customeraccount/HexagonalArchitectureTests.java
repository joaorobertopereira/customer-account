package br.com.helpcsistemas.customeraccount;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

/** Regras de arquitetura limpa/hexagonal aplicadas a todos os módulos de negócio. */
@AnalyzeClasses(packages = "br.com.helpcsistemas.customeraccount", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTests {

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta..",
                    "com.fasterxml..",
                    "tools.jackson..",
                    "io.kurrent..",
                    "org.bson..",
                    "com.mongodb..")
            .because("o domínio deve ser Java puro");

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..application..", "..adapter..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..adapter..")
            .because("a aplicação fala com o mundo externo apenas por portas");

    @ArchTest
    static final ArchRule applicationDoesNotTouchInfrastructure = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "io.kurrent..",
                    "org.springframework.data..",
                    "com.mongodb..",
                    "br.com.helpcsistemas.customeraccount.eventsourcing..",
                    "br.com.helpcsistemas.customeraccount.privacy..");

    @ArchTest
    static final ArchRule inboundAdaptersDoNotUseOutboundAdapters = noClasses()
            .that()
            .resideInAPackage("..adapter.in..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..adapter.out..");

    @ArchTest
    static final ArchRule controllersLiveInWebAdapters =
            classes().that().areAnnotatedWith(RestController.class).should().resideInAPackage("..adapter.in.web..");

    @ArchTest
    static final ArchRule onlyEventSourcingModuleTalksToKurrentDb = noClasses()
            .that()
            .resideOutsideOfPackage("br.com.helpcsistemas.customeraccount.eventsourcing..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("io.kurrent..");
}
