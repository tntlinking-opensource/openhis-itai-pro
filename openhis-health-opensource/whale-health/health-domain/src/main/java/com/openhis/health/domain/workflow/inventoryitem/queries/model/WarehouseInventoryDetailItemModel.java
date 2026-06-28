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

package com.openhis.health.domain.workflow.inventoryitem.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药库库存明细现库行查询模型（与 {@code wkf_inventory_item} 主列对齐，供应用层装配定价与产品定义列）。
 */
@Getter
@Setter
public class WarehouseInventoryDetailItemModel {

    /** 主键。 */
    private Long id;

    /** 物品表名。 */
    private String itemTable;

    /** 物品定义主键。 */
    private Long itemId;

    /** 冗余名称。 */
    private String name;

    /** 拼音码。 */
    private String pyStr;

    /** 五笔码。 */
    private String wbStr;

    /** 批号。 */
    private String lotNumber;

    /** 拆零最小单位账面数量。 */
    private BigDecimal quantity;

    /** 计量单位编码。 */
    private String unitCode;

    /** 库房位置主键。 */
    private Long warehouseId;

    /** 供应商标识。 */
    private Long supplierId;

    /** 效期。 */
    private LocalDateTime expirationDate;

    /** 生产日期。 */
    private LocalDateTime productionDate;

    /** 供应状态持久化码。 */
    private String inventoryStatusEnum;

    /** 追溯码（{@code wkf_inventory_item.trace_no}）。 */
    private String traceNo;

    /** 距效期剩余天数（由效期与当前自然日计算，可为负）。 */
    private Integer remainingDays;
}
