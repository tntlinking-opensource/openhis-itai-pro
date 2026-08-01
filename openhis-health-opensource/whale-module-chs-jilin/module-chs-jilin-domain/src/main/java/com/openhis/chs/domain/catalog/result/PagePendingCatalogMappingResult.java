/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * Workbench 分页结果 */
@Data
@Accessors(chain = true)
@Schema(description = "待对码分页结果")
public class PagePendingCatalogMappingResult {

    private List<PendingCatalogMappingRowResult> rows = new ArrayList<>();
    private long total;
    private int pageNo;
    private int pageSize;
    private long pendingUploadCount;
    /** 平台镜像无对照的院内启用品项数（待处理总数） */
    private long notOnPlatformCount;
    private long noYbNoCount;

    /** @deprecated 请用 {@link #notOnPlatformCount}；保留兼容旧前端 */
    @Deprecated
    private long matchedCount;
}
