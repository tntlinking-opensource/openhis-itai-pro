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

package com.openhis.health.application.inventory.warehouse.purchaseorder.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 采购选品分页行。
 */
@Getter
@Setter
public class ProductDefinitionItemDto {

    /** 产品定义主键。 */
    private Long productDefinitionId;
    /** 物品来源表名。 */
    private String productTableName;
    /**
     * 目录业务流水号：药品/耗材/器材定义 {@code bus_no}，与 {@link com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto#getProductBusNo()} 一致。
     */
    private String productBusNo;
    /** 产品名称。 */
    private String productName;
    /** 商品名。 */
    private String merchandiseName;
    /** 单位编码。 */
    private String unitCode;
    /** 最小单位编码。 */
    private String minUnitCode;
    /** 分类编码。 */
    private String categoryCode;

    /** 物资分类展示名（术语 {@code MEDICATION_CATEGORY_CODE} / {@code DEVICE_CATEGORY_CODE}）。 */
    private String categoryCodeDictText;

    /** 基础售价（定价表 {@code SALE} 实例维）。 */
    private BigDecimal price;
    /** 采购价。 */
    private BigDecimal purchasePrice;
    /** 厂家名称。 */
    private String manufacturer;
    /** 拼音检索码。 */
    private String pyStr;
    /** 五笔检索码。 */
    private String wbStr;
}
