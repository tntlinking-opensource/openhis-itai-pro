/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.module;

import java.util.List;

/**
 * 聚合 classpath 上全部 {@link OpenHisFlywayModuleDefinition}。
 */
public final class OpenHisFlywayModuleCatalog {

    private static final List<OpenHisFlywayModuleDefinition> ENTRIES =
            List.copyOf(OpenHisClasspathModuleResources.loadAll(
                    "META-INF/openhis/flyway-module.json", OpenHisFlywayModuleDefinition.class));

    private OpenHisFlywayModuleCatalog() {
    }

    public static List<OpenHisFlywayModuleDefinition> entries() {
        return ENTRIES;
    }
}
