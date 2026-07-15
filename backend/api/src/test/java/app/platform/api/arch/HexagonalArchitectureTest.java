package app.platform.api.arch;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/**
 * The hexagonal constraint as a failing test, not a convention (3.01 enhancement):
 * the domain depends on nothing infrastructural.
 */
@AnalyzeClasses(packages = "app.platform")
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domainHasNoInfrastructureImports = ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("app.platform.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "software.amazon.awssdk..",
                    "com.fasterxml.jackson..",
                    "jakarta.persistence..",
                    "jakarta.servlet..")
            .because("the :domain module is the hexagonal core — ports only, no adapters");

    @ArchTest
    static final ArchRule controllersDoNotTouchPersistenceEntities = ArchRuleDefinition.noClasses()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("app.platform.persistence.entity..")
            .because("controllers speak DTOs and domain types; JPA entities stay behind adapters")
            .allowEmptyShould(true);
}
