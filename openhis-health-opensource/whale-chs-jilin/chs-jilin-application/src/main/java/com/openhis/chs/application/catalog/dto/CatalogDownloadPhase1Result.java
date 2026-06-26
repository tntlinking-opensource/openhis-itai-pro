/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.dto;

import java.util.List;

/**
 * 阶段一结果。正式下载不设轮次上限；{@code stoppedByPreviewRoundCap} 仅探测显式 {@code maxVersions} 时可能为 true。
 */
public record CatalogDownloadPhase1Result(
    List<CatalogDownloadVersionBundle> bundles,
    boolean stoppedByPreviewRoundCap) {

    public CatalogDownloadPhase1Result {
        if (bundles == null) {
            bundles = List.of();
        } else {
            bundles = List.copyOf(bundles);
        }
    }

    public CatalogDownloadPhase1Result(List<CatalogDownloadVersionBundle> bundles) {
        this(bundles, false);
    }
}
