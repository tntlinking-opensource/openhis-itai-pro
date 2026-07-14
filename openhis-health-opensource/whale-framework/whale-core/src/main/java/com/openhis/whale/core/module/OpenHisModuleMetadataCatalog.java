/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.module;

import java.util.List;

/**
 * 聚合 classpath 上全部 {@link OpenHisModuleMetadata}。
 */
public final class OpenHisModuleMetadataCatalog {

    private static final List<OpenHisModuleMetadata> ENTRIES =
            List.copyOf(OpenHisClasspathModuleResources.loadAll(
                    "META-INF/openhis/module-metadata.json", OpenHisModuleMetadata.class));

    private OpenHisModuleMetadataCatalog() {
    }

    public static List<OpenHisModuleMetadata> entries() {
        return ENTRIES;
    }
}
