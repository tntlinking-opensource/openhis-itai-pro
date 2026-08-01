/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.bridge.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 回归：CHS + 工伤同时启用时，Wi 不得再扫 {@code com.openhis.chs.**.repository}，
 * 否则与 {@code ChsModuleDomainAutoConfiguration} 重复注册（BeanDefinitionOverrideException）。
 */
class WiChsJpaRepositoryScanOverlapTest {

    /** 与 ChsModuleDomainAutoConfiguration 保持同步。 */
    private static final List<String> CHS_REPOSITORY_PACKAGES = List.of(
            "com.openhis.chs.domain.catalog.repository",
            "com.openhis.chs.domain.notice.repository",
            "com.openhis.chs.domain.inpatient.repository",
            "com.openhis.chs.domain.inventory.repository",
            "com.openhis.chs.domain.outpatient.repository",
            "com.openhis.chs.domain.platformdict.repository",
            "com.openhis.chs.domain.reconciliation.repository",
            "com.openhis.chs.domain.session.repository",
            "com.openhis.chs.domain.settlement.repository",
            "com.openhis.chs.domain.invoke.repository");

    @Test
    void wiModuleMustNotScanChsRepositoryPackages() {
        EnableJpaRepositories wi = WiModuleDomainAutoConfiguration.class.getAnnotation(EnableJpaRepositories.class);
        assertNotNull(wi);

        Set<String> packages = resolvedPackages(wi);
        assertFalse(packages.isEmpty(), "Wi Module 应至少扫描一个 Repository 包");
        for (String pkg : packages) {
            assertFalse(
                    pkg.startsWith("com.openhis.chs"),
                    "WiModule 不得扫描 CHS repository 包（会导致与 ChsModule 重复注册）: " + pkg);
            assertTrue(
                    pkg.startsWith("com.openhis.wi"),
                    "WiModule repository 扫描应落在 com.openhis.wi.*: " + pkg);
        }
    }

    @Test
    void wiAndChsRepositoryPackagesMustNotOverlap() {
        EnableJpaRepositories wi = WiModuleDomainAutoConfiguration.class.getAnnotation(EnableJpaRepositories.class);
        assertNotNull(wi);

        Set<String> wiPackages = resolvedPackages(wi);
        for (String wiPkg : wiPackages) {
            for (String chsPkg : CHS_REPOSITORY_PACKAGES) {
                if (packagesOverlap(wiPkg, chsPkg)) {
                    fail("Wi/Chs JPA 扫描包重叠: wi=" + wiPkg + " chs=" + chsPkg);
                }
            }
        }
    }

    private static Set<String> resolvedPackages(EnableJpaRepositories ann) {
        Set<String> packages = new HashSet<>(Arrays.asList(ann.basePackages()));
        packages.addAll(Stream.of(ann.basePackageClasses())
                .map(Class::getPackageName)
                .collect(Collectors.toSet()));
        packages.removeIf(String::isBlank);
        return packages;
    }

    private static boolean packagesOverlap(String a, String b) {
        return a.equals(b) || a.startsWith(b + ".") || b.startsWith(a + ".");
    }
}
