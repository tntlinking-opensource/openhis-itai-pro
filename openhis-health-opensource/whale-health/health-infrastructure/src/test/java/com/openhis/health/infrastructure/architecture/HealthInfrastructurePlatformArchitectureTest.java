/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * platform 层守卫（S7）：禁止再现 legacy {@code WhaleRoleLookup} 直连 whale-identity Repository 模式。
 */
class HealthInfrastructurePlatformArchitectureTest {

    private static final JavaClasses INFRASTRUCTURE =
        new ClassFileImporter().importPackages("com.openhis.health.infrastructure");

    @Test
    void platformMustNotDependOnWhaleIdentityRepositories() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage("com.openhis.health.infrastructure.platform..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.openhis.whale.identity.infrastructure.repository..")
            .allowEmptyShould(true);
        rule.check(INFRASTRUCTURE);
    }
}
