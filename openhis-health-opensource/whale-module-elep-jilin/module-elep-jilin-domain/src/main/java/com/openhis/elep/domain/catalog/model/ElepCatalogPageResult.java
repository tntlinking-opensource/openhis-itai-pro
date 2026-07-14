/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.catalog.model;

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
    private List<ElepCatalogItem> items = new ArrayList<>();
}
