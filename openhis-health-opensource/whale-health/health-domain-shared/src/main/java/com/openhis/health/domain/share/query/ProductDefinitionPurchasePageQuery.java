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

import java.util.List;

/**
 * 采购/进货选品分页入参（读主表；由应用层 {@code ProductDefinitionPageQuery} 转入）。
 */
@Getter
@Setter
public class ProductDefinitionPurchasePageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 产品来源表名（单表；药库场景通常为药品或耗材表）。 */
    private String productTableName;

    /** 分类编码。 */
    private String categoryCode;

    /** 启用标志。 */
    private Boolean activeFlag;

    /** 供应商标识（药品/耗材 {@code supply_id}）。 */
    private Long supplierId;

    /** 厂家名称。 */
    private String manufacturer;

    /** 预选产品键，与关键字 OR 组合。 */
    private List<ProductDefinitionBatchLookup> prepickedProductKeys;

    /** 排序字段（可选；默认按创建时间降序）。 */
    private String sortField;

    /** 排序方向。 */
    private String sortOrder;
}
