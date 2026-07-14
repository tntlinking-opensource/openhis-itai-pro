/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.catalog.support.dto;

import java.util.List;

/**
 * 目录下载阶段一工作流结果。
 * <p>正式下载不设轮次上限；{@code stoppedByPreviewRoundCap} 仅在探测模式显式传入
 * {@code maxVersions} 且达到上限时为 {@code true}。</p>
 *
 * @param bundles                  按版本链顺序收集的版本包列表
 * @param stoppedByPreviewRoundCap 是否因探测轮次上限提前停止
 */
public record CatalogDownloadPhase1Result(
    List<CatalogDownloadVersionBundle> bundles,
    boolean stoppedByPreviewRoundCap) {

    /** 规范化 bundles 为不可变副本。 */
    public CatalogDownloadPhase1Result {
        if (bundles == null) {
            bundles = List.of();
        } else {
            bundles = List.copyOf(bundles);
        }
    }

    /** 构造阶段一结果（非探测截断场景）。 */
    public CatalogDownloadPhase1Result(List<CatalogDownloadVersionBundle> bundles) {
        this(bundles, false);
    }
}
