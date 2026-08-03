package com.quorumtrading.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/**
 * ArchUnit tests to enforce naming conventions.
 * These tests ensure consistent naming across the codebase.
 */
class NamingConventionTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.quorumtrading");
    }

    @Test
    void repositoryInterfacesShouldHaveCorrectNaming() {
        ArchRule rule = classes()
            .that().areInterfaces()
            .and().haveSimpleNameEndingWith("Repository")
            .should().resideInAPackage("com.quorumtrading.application.port..")
            .because("Repository interfaces must be port interfaces in application layer");

        rule.check(classes);
    }

    @Test
    void repositoryImplementationsShouldHaveCorrectNaming() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("RepositoryImpl")
            .should().resideInAPackage("com.quorumtrading.adapter.persistence..")
            .andShould().haveSimpleNameContaining("Repository")
            .because("Repository implementations must be in persistence adapter");

        rule.check(classes);
    }

    @Test
    void jpaEntitiesShouldBeInEntityPackage() {
        ArchRule rule = classes()
            .that().areAnnotatedWith("jakarta.persistence.Entity")
            .or().haveSimpleNameEndingWith("JpaEntity")
            .should().resideInAPackage("com.quorumtrading.adapter.persistence.entity..")
            .because("JPA entities are infrastructure concerns and must be in persistence adapter entity package");

        rule.check(classes);
    }

    @Test
    void restResourcesShouldHaveCorrectNaming() {
        ArchRule rule = classes()
            .that().areAnnotatedWith("jakarta.ws.rs.Path")
            .and().resideInAPackage("com.quorumtrading.adapter.rest..")
            .should().haveSimpleNameEndingWith("Resource")
            .andShould().resideInAPackage("com.quorumtrading.adapter.rest.resource..")
            .because("REST resources (not REST clients) must follow naming convention");

        rule.check(classes);
    }

    @Test
    void valueObjectsShouldBeInValueObjectPackage() {
        ArchRule rule = classes()
            .that().resideInAPackage("com.quorumtrading.model.valueobject..")
            .should().beRecords()
            .orShould().haveOnlyFinalFields()
            .because("Value objects should be immutable (records or classes with final fields)");

        rule.check(classes);
    }

    @Test
    void domainEntitiesShouldBeInEntityPackage() {
        ArchRule rule = classes()
            .that().resideInAPackage("com.quorumtrading.model.entity..")
            .should().beRecords()
            .orShould().notBeAnnotatedWith("jakarta.persistence.Entity")
            .because("Domain entities are pure domain concepts, not JPA entities");

        rule.check(classes);
    }

    @Test
    void portsShouldBeInterfaces() {
        ArchRule rule = classes()
            .that().resideInAPackage("com.quorumtrading.application.port..")
            .and().haveSimpleNameEndingWith("Port")
            .or().haveSimpleNameEndingWith("Repository")
            .should().beInterfaces()
            .because("Port interfaces (ending with Port or Repository) must be interfaces");

        rule.check(classes);
    }

    @Test
    void dtosShouldBeInDtoPackage() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Request")
            .or().haveSimpleNameEndingWith("Response")
            .and().haveSimpleNameNotContaining("TextResponse") // ChatStreamEvent.TextResponse is a sealed variant, not a DTO
            .should().resideInAPackage("..dto..")
            .because("DTOs (Request/Response objects) must be in dto packages");

        rule.check(classes);
    }

    @Test
    void mappersShouldBeInMapperPackage() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Mapper")
            .and().haveSimpleNameNotEndingWith("ExceptionMapper")
            // SaxoExchangeIdMapper is a pure, package-private static MIC→ExchangeId lookup table
            // (no I/O, no CDI) that lives next to its sole user (SaxoUicResolver) in the adapter
            // root, not a relocatable mapper-package bean — same spirit as the ExceptionMapper carve-out.
            .and().haveSimpleNameNotContaining("ExchangeIdMapper")
            .should().resideInAPackage("..mapper..")
            .because("Mappers (except ExceptionMappers) must be in mapper packages");

        rule.check(classes);
    }
}
