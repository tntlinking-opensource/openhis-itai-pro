/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.module;

import java.util.List;

/**
 * 聚合 classpath 上全部 {@link ZxplatFlywayModuleDefinition}。
 */
public final class ZxplatFlywayModuleCatalog {

    private static final List<ZxplatFlywayModuleDefinition> ENTRIES =
            List.copyOf(ZxplatClasspathModuleResources.loadAll(
                    "META-INF/zxplat/flyway-module.json", ZxplatFlywayModuleDefinition.class));

    private ZxplatFlywayModuleCatalog() {
    }

    public static List<ZxplatFlywayModuleDefinition> entries() {
        return ENTRIES;
    }
}
