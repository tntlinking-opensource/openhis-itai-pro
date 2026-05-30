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
 * 库房单据明细列表行（订货分页）。
 */
@Getter
@Setter
public class WarehouseDocumentDetailRowDto {

    /** 读模型行主键。 */
    private Long id;
    /** 单据号。 */
    private String busNo;
    /** 单据类型（对外序列化为整数码）。 */
    private Integer typeEnum;
    /** 单据状态（对外序列化为整数码）。 */
    private Integer statusEnum;
    /** 源位置。 */
    private Long sourceLocationId;
    /** 目标位置。 */
    private Long purposeLocationId;
    /** 供应商。 */
    private Long supplierId;
    /** 物品标识。 */
    private Long itemId;
    /** 物品来源表。 */
    private String itemTable;
    /** 数量。 */
    private BigDecimal itemQuantity;
    /** 单位编码。 */
    private String unitCode;
    /** 制单时间（与 {@code wkf_supply_request.occurrence_time} 对齐）。 */
    private LocalDateTime occurrenceTime;
    /** 申请时间。 */
    private LocalDateTime applyTime;
    /** 审批时间。 */
    private LocalDateTime approvalTime;
    /** 行总价。 */
    private BigDecimal totalPrice;
}
