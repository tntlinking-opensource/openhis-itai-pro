/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.catalog.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 目录搜索分页结果。 */
@Data
@Accessors(chain = true)
public class ElepCatalogPageResult {

    private long total;
    private int page;
    private int pageSize;
    private List<ElepCatalogItemDto> items = new ArrayList<>();
}
