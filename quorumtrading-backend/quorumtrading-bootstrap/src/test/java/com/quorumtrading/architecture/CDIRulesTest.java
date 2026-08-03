package com.quorumtrading.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/**
 * ArchUnit tests to enforce CDI rules and best practices.
 * These tests ensure proper dependency injection patterns.
 */
class CDIRulesTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.quorumtrading");
    }

    @Test
    void repositoryImplementationsShouldBeApplicationScoped() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("RepositoryImpl")
            .should().beAnnotatedWith("jakarta.enterprise.context.ApplicationScoped")
            .because("Repository implementations should be CDI beans with ApplicationScoped lifecycle");

        rule.check(classes);
    }

    @Test
    void restResourcesShouldBeApplicationScoped() {
        ArchRule rule = classes()
            .that().areAnnotatedWith("jakarta.ws.rs.Path")
            .and().resideInAPackage("com.quorumtrading.adapter.rest..")
            .should().beAnnotatedWith("jakarta.enterprise.context.ApplicationScoped")
            .because("REST resources (not REST clients) should be ApplicationScoped");

        rule.check(classes);
    }

    @Test
    void adaptersShouldBeApplicationScoped() {
        ArchRule llmAdapterRule = classes()
            .that().resideInAPackage("com.quorumtrading.adapter.llm..")
            .and().haveSimpleNameEndingWith("Adapter")
            .should().beAnnotatedWith("jakarta.enterprise.context.ApplicationScoped")
            .because("LLM adapters should be CDI beans with ApplicationScoped lifecycle");

        ArchRule marketDataAdapterRule = classes()
            .that().resideInAPackage("com.quorumtrading.adapter.marketdata..")
            .and().haveSimpleNameEndingWith("Adapter")
            .should().beAnnotatedWith("jakarta.enterprise.context.ApplicationScoped")
            .because("Market data adapters should be CDI beans with ApplicationScoped lifecycle");

        llmAdapterRule.check(classes);
        marketDataAdapterRule.check(classes);
    }

    @Test
    void useCasesShouldNotHaveCdiAnnotations() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quorumtrading.application.usecase..")
            .and().haveSimpleNameEndingWith("UseCase")
            .should().beAnnotatedWith("jakarta.enterprise.context.ApplicationScoped")
            .orShould().beAnnotatedWith("jakarta.enterprise.context.RequestScoped")
            .orShould().beAnnotatedWith("jakarta.inject.Inject")
            .because("Use cases should be pure - CDI beans are created via @Produces in ApplicationConfig");

        rule.check(classes);
    }

    @Test
    void servicesShouldNotHaveCdiAnnotations() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quorumtrading.application.service..")
            .and().haveSimpleNameEndingWith("Service")
            .and().haveSimpleNameNotContaining("BackFillTickerPriceDataService") // Exclude work-in-progress service
            .and().haveSimpleNameNotEndingWith("Executor")  // Exclude BackfillExecutor - needs Instance<MarketDataPort> and Event
            .and().haveSimpleNameNotEndingWith("Tracker")  // Exclude RateLimitTracker - stateful singleton
            .should().beAnnotatedWith("jakarta.enterprise.context.ApplicationScoped")
            .orShould().beAnnotatedWith("jakarta.enterprise.context.RequestScoped")
            .orShould().beAnnotatedWith("jakarta.inject.Inject")
            .because("Application services should be pure - CDI beans are created via @Produces in ApplicationConfig");

        rule.check(classes);
    }

    @Test
    void dtosShouldNotBeCdiBeans() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..dto..")
            .should().beAnnotatedWith("jakarta.enterprise.context.ApplicationScoped")
            .orShould().beAnnotatedWith("jakarta.enterprise.context.RequestScoped")
            .orShould().beAnnotatedWith("jakarta.enterprise.context.SessionScoped")
            .orShould().beAnnotatedWith("jakarta.enterprise.context.Dependent")
            .because("DTOs should be simple data carriers, not CDI beans");

        rule.check(classes);
    }

    @Test
    void domainModelShouldNotBeCdiBeans() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quorumtrading.model..")
            .should().beAnnotatedWith("jakarta.enterprise.context.ApplicationScoped")
            .orShould().beAnnotatedWith("jakarta.enterprise.context.RequestScoped")
            .orShould().beAnnotatedWith("jakarta.enterprise.context.SessionScoped")
            .orShould().beAnnotatedWith("jakarta.enterprise.context.Dependent")
            .orShould().beAnnotatedWith("jakarta.inject.Inject")
            .because("Domain model should be pure and not have CDI annotations");

        rule.check(classes);
    }

    @Test
    void mappersShouldBeApplicationScoped() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Mapper")
            .and().haveSimpleNameNotEndingWith("ExceptionMapper")
            // SaxoExchangeIdMapper is a pure, package-private static MIC→ExchangeId lookup table
            // (final class, private ctor, no I/O, no CDI) — deliberately not a bean, like the
            // ExceptionMapper carve-out above.
            .and().haveSimpleNameNotContaining("ExchangeIdMapper")
            .and().resideInAPackage("com.quorumtrading.adapter..")
            .should().beAnnotatedWith("jakarta.enterprise.context.ApplicationScoped")
            .because("Mappers (except ExceptionMappers) should be CDI beans");

        rule.check(classes);
    }
}
