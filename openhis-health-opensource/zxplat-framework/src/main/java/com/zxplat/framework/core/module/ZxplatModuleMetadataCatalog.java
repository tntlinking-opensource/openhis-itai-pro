/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.module;

import java.util.List;

/**
 * 聚合 classpath 上全部 {@link ZxplatModuleMetadata}。
 */
public final class ZxplatModuleMetadataCatalog {

    private static final List<ZxplatModuleMetadata> ENTRIES =
            List.copyOf(ZxplatClasspathModuleResources.loadAll(
                    "META-INF/zxplat/module-metadata.json", ZxplatModuleMetadata.class));

    private ZxplatModuleMetadataCatalog() {
    }

    public static List<ZxplatModuleMetadata> entries() {
        return ENTRIES;
    }
}
