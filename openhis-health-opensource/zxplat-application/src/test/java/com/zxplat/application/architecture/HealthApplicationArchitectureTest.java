/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.architecture;

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
        new ClassFileImporter().importPackages("com.zxplat.application");

    @Test
    void sharedAndSupportMustNotDependOnRepositoriesOrJpa() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(
                "com.zxplat.application.shared..",
                "com.zxplat.application..support..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..repository..",
                "jakarta.persistence..")
            .allowEmptyShould(true);
        rule.check(APPLICATION);
    }

    @Test
    void sharedAndSupportMustNotDependOnFrameworkIdentityRepositories() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(
                "com.zxplat.application.shared..",
                "com.zxplat.application..support..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.zxplat.framework.identity.infrastructure.repository..")
            .allowEmptyShould(true);
        rule.check(APPLICATION);
    }

    @Test
    void applicationMustNotDependOnWorkInjuryDomain() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage("com.zxplat.application..")
            .should().dependOnClassesThat().resideInAnyPackage("com.zxplat.plugin.wi..")
            .allowEmptyShould(true);
        rule.check(APPLICATION);
    }

    /**
     * S8 §12.4：shared 禁止依赖 feature 包（默认跟页面走，shared 是例外）。
     */
    @Test
    void sharedMustNotDependOnFeaturePackages() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage("com.zxplat.application.shared..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.zxplat.application.outpatient..",
                "com.zxplat.application.inpatient..",
                "com.zxplat.application.inventory..",
                "com.zxplat.application.basic..",
                "com.zxplat.application.finance..",
                "com.zxplat.application.mrms..",
                "com.zxplat.application.report..",
                "com.zxplat.application.print..")
            .allowEmptyShould(true);
        rule.check(APPLICATION);
    }
}
