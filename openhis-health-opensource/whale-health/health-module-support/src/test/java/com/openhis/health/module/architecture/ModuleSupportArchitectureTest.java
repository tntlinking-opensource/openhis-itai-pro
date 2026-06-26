/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * health-module-support 读路径架构守卫（P2）：querysupport / module.shared 禁止直连 Repository。
 */
class ModuleSupportArchitectureTest {

    private static final JavaClasses MODULE_SUPPORT =
        new ClassFileImporter().importPackages(
            "com.openhis.health.module.querysupport",
            "com.openhis.health.module.shared");

    @Test
    void querysupportAndSharedMustNotDependOnRepositories() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(
                "com.openhis.health.module.querysupport..",
                "com.openhis.health.module.shared..")
            .should().dependOnClassesThat().resideInAnyPackage("..repository..");
        rule.check(MODULE_SUPPORT);
    }

    @Test
    void querysupportMustNotDependOnJakartaPersistence() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage("com.openhis.health.module.querysupport..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..");
        rule.check(MODULE_SUPPORT);
    }
}
