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
import java.time.LocalDateTime;

/**
 * 订货单行（新建/编辑请求共用）。
 */
@Getter
@Setter
public class PurchaseOrderLineDto {

    /** 物品来源表。 */
    private String itemTable;
    /** 物品定义主键。 */
    private Long itemId;
    /** 数量。 */
    private BigDecimal itemQuantity;
    /** 单位编码。 */
    private String unitCode;
    /** 单价。 */
    private BigDecimal price;
    /** 行总价。 */
    private BigDecimal totalPrice;
    /** 期望到货等业务时间。 */
    private LocalDateTime occurrenceTime;
    /** 行备注。 */
    private String remake;
}
