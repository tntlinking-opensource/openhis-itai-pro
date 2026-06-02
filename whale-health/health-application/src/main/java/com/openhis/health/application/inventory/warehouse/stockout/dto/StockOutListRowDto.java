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

package com.openhis.health.application.inventory.warehouse.stockout.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 *
 * <p>类型、状态等列为持久化 {@code code}（B.10.8.1）；{@code statusEnumText} 与目的展示名由列表查询后在应用层装配。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药库出库单列表聚合行")
public class StockOutListRowDto {

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;
    /** 供应类型持久化码。 */
    @Schema(description = "供应类型持久化码，固定为药库出库 CABINET_STOCK_OUT")
    private String typeEnum;
    /** 单据状态持久化码。 */
    @Schema(description = "单据状态持久化码（SupplyStatus#getCode）")
    private String statusEnum;
    /** 单据状态展示文案（{@link com.openhis.health.domain.share.enums.SupplyStatus#getDisplay()}）。 */
    @Schema(description = "单据状态中文展示（SupplyStatus#getDisplay）")
    private String statusEnumText;
    /** 源药库位置主键。 */
    @Schema(description = "源药库位置主键")
    private Long sourceLocationId;
    /** 目的位置主键。 */
    @Schema(description = "目的位置主键")
    private Long purposeLocationId;
    /** 目的位置展示名。 */
    @Schema(description = "目的位置展示名（到何处）")
    private String purposeLocationName;
    /** 申请时间（提交审批时服务端写入）。 */
    @Schema(description = "申请时间（apply_time）")
    private LocalDateTime applyTime;
    /** 审批时间。 */
    @Schema(description = "审批时间")
    private LocalDateTime approvalTime;
    /** 制单时间（明细 occurrence_time 组内最大值）。 */
    @Schema(description = "制单时间（occurrence_time 聚合）")
    private LocalDateTime occurrenceTime;
    /** 申请人主键。 */
    @Schema(description = "申请人主键")
    private Long applicantId;
    /** 制单人（申请人）展示名。 */
    @Schema(description = "制单人展示名")
    private String creatorName;
    /** 审批人主键。 */
    @Schema(description = "审批人主键")
    private Long approverId;
    /** 审批人展示名。 */
    @Schema(description = "审批人展示名")
    private String approverName;
    /** 该单各明细 {@code total_price} 之和。 */
    @Schema(description = "单据总金额")
    private BigDecimal totalAmount;
    /** 关联上游单号（如请领）。 */
    @Schema(description = "关联上游业务单号")
    private String originalBusNo;
}
