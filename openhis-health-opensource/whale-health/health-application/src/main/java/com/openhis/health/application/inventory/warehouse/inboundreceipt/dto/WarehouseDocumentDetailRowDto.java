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

package com.openhis.health.application.inventory.warehouse.inboundreceipt.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 与同读模型装配，亦复用本 DTO；其它库房单据分页可再复用）。
 *
 * <p>与 {@link com.openhis.health.domain.workflow.supplyrequest.query.WarehouseDocumentDetailQueryModel} 列语义对齐；
 * 字段名含 {@code Enum} 或业务类型/状态/类别语义且类型为 {@link String} 者，均为持久化 {@code getCode()}（B.10.8.1），与领域查询模型一致。</p>
 */
@Getter
@Setter
public class WarehouseDocumentDetailRowDto {

    /** 读模型行主键。 */
    private Long id;
    /** 业务单号。 */
    private String busNo;
    /** 来源订货等业务单号。 */
    private String originalBusNo;
    /** 单据类型持久化码（{@link com.openhis.health.domain.share.enums.SupplyType#getCode()}）。 */
    private String typeEnum;
    /** 单据状态持久化码（{@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()}）。 */
    private String statusEnum;
    /** 供应类别持久化码（{@link com.openhis.health.domain.share.enums.SupplyCategory#getCode()}）。 */
    private String categoryEnum;
    /** 源位置主键。 */
    private Long sourceLocationId;
    /** 目标位置主键。 */
    private Long purposeLocationId;
    /** 供应商主键。 */
    private Long supplierId;
    /** 物品定义主键。 */
    private Long itemId;
    /** 物品来源表名。 */
    private String itemTable;
    /** 数量。 */
    private BigDecimal itemQuantity;
    /** 计量单位编码（字典持久化码，B.10.8.1）。 */
    private String unitCode;
    /** 批号。 */
    private String lotNumber;
    /** 追溯码。 */
    private String traceNo;
    /** 单价。 */
    private BigDecimal price;
    /** 行总价。 */
    private BigDecimal totalPrice;
    /** 申请时间。 */
    private LocalDateTime applyTime;
    /** 审批时间。 */
    private LocalDateTime approvalTime;
}
