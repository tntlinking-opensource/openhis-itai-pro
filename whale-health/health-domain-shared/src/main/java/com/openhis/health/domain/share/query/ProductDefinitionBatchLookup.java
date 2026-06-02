/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
