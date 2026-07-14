/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.query;

import lombok.Getter;
import lombok.Setter;

/** 产品定义医保目录编码读侧投影（跨聚合批量加载）。 */
@Getter
@Setter
public class ProductDefinitionCatalogQueryModel {

    private String productTableName;
    private Long productDefinitionId;
    private String productBusNo;
    private String ybNo;
}
