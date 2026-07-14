/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.catalog.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 目录 keyword 搜索请求。 */
@Data
@Accessors(chain = true)
public class ElepCatalogSearchRequest {

    private Long contractId;
    private String contractBusNo;
    private String keyword;
    /** 药品 1301 明细模式可选筛选。 */
    private String aprvno;
    private String spec;
    private int page;
    private int pageSize;
}
