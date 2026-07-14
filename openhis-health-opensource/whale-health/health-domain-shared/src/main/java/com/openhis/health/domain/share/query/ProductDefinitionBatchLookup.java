/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.query;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 产品定义批量加载键（来源表名 + 定义主键，与 SupplyRequest item_table / item_id 及读模型批量查询对齐）。
 */
@Getter
@Setter
public class ProductDefinitionBatchLookup implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 物品来源表名（原读模型 {@code product_table_name}）。 */
    private String productTableName;

    /** 物品定义主键（原读模型 {@code product_definition_id}）。 */
    private Long productDefinitionId;
}
