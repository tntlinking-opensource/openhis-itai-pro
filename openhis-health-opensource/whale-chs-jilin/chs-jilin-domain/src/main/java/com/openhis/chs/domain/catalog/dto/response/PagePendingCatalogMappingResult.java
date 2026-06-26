/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * Workbench 分页结果。
 */
@Data
@Accessors(chain = true)
@Schema(description = "待对码分页结果")
public class PagePendingCatalogMappingResult {

    private List<PendingCatalogMappingRowDto> rows = new ArrayList<>();
    private long total;
    private int pageNo;
    private int pageSize;
    private long pendingUploadCount;
    private long matchedCount;
    private long noYbNoCount;
}
