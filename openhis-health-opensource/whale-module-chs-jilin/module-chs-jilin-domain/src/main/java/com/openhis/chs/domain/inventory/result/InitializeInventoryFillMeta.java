/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 批初始化分页扫描元数据（Loader →Bridge 回填 Result） */
@Data
@Accessors(chain = true)
public class InitializeInventoryFillMeta {

    private Integer pageNo;
    private Integer nextPageNo;
    private Boolean hasMore;
    private Long totalElements;
    private Integer totalPages;
    private Integer skippedAlreadyInitialized;
}
