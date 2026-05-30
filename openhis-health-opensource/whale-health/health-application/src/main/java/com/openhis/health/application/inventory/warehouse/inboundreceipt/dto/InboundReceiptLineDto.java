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
 *
 * <p>与领域 {@code wkf_supply_request} 行字段对齐；{@code unitCode} 等为字典或计量持久化码（B.10.8.1）。
 * {@link #traceNo} 可含多个片段（逗号分隔），同意路径下受追溯宽限校验（WHS-IN-SDD-R-008 / {@code WHS_IN_001}/{@code WHS_IN_004}）。</p>
 */
@Getter
@Setter
public class InboundReceiptLineDto {

    /**
     * 物品来源物理表名，与供应请求 {@code item_table} 及选品 {@code productTableName} 白名单一致。
     */
    private String itemTable;

    /**
     * 物品定义主键，与 {@code item_id} 一致。
     */
    private Long itemId;

    /**
     * 进货数量（业务口径正数；拆零入账由同意路径结合产品定义另行换算）。
     */
    private BigDecimal itemQuantity;

    /**
     * 计量单位编码（字典/持久化码），与行数量同一语义维度。
     */
    private String unitCode;

    /**
     * 批号；同意入库前须非空（应用层 {@code resolveLotNumberForInventory}）；新建/编辑请求可暂空由业务另行约束。
     */
    private String lotNumber;

    /**
     * 追溯码；可空。非空时可为逗号分隔多码，须满足宽限长度（应用层校验）。
     */
    private String traceNo;

    /**
     * 含税或约定口径下的单价；可与 {@link #totalPrice}、数量做交叉校验（规则以领域为准）。
     */
    private BigDecimal price;

    /**
     * 行总价。
     */
    private BigDecimal totalPrice;

    /**
     * 期望到货或业务发生时刻。
     */
    private LocalDateTime occurrenceTime;

    /**
     * 效期区间起点（落库 {@code wkf_supply_request.start_time}）；可空。
     */
    private LocalDateTime startTime;

    /**
     * 效期区间止点（落库 {@code wkf_supply_request.end_time}）；可空。
     */
    private LocalDateTime endTime;

    /**
     * 行级备注。
     */
    private String remake;
}
