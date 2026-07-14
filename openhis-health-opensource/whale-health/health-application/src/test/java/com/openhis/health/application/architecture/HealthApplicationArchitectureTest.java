/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * health-application L2 读/写编排守卫（S7）：shared 与 feature support 禁止直连 Repository / JPA。
 */
class HealthApplicationArchitectureTest {

    private static final JavaClasses APPLICATION =
        new ClassFileImporter().importPackages("com.openhis.health.application");

    @Test
    void sharedAndSupportMustNotDependOnRepositoriesOrJpa() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(
                "com.openhis.health.application.shared..",
                "com.openhis.health.application..support..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..repository..",
                "jakarta.persistence..")
            .allowEmptyShould(true);
        rule.check(APPLICATION);
    }

    @Test
    void sharedAndSupportMustNotDependOnWhaleIdentityRepositories() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(
                "com.openhis.health.application.shared..",
                "com.openhis.health.application..support..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.openhis.whale.identity.infrastructure.repository..")
            .allowEmptyShould(true);
        rule.check(APPLICATION);
    }

    @Test
    void applicationMustNotDependOnWorkInjuryDomain() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage("com.openhis.health.application..")
            .should().dependOnClassesThat().resideInAnyPackage("com.openhis.wi..")
            .allowEmptyShould(true);
        rule.check(APPLICATION);
    }

    /**
     * S8 §12.4：shared 禁止依赖 feature 包（默认跟页面走，shared 是例外）。
     */
    @Test
    void sharedMustNotDependOnFeaturePackages() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage("com.openhis.health.application.shared..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.openhis.health.application.outpatient..",
                "com.openhis.health.application.inpatient..",
                "com.openhis.health.application.inventory..",
                "com.openhis.health.application.basic..",
                "com.openhis.health.application.finance..",
                "com.openhis.health.application.mrms..",
                "com.openhis.health.application.report..",
                "com.openhis.health.application.print..")
            .allowEmptyShould(true);
        rule.check(APPLICATION);
    }
}
