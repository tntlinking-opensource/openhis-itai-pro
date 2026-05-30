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

package com.openhis.health.application.inventory.pharmacy.stocktake.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药房盘点单列表聚合行（IF-PHA-ST-02：每 {@code bus_no} 一行）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "药房盘点单列表聚合行")
public class PharmacyStocktakeListRowDto {

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;

    /** 单据状态持久化码。 */
    @Schema(description = "单据状态 SupplyStatus#getCode")
    private String statusEnum;

    /** 状态展示文案。 */
    @Schema(description = "状态中文展示")
    private String statusEnumText;

    /** 盘点药房位置主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "盘点药房 purposeLocationId")
    private Long purposeLocationId;

    /** 盘点药房展示名。 */
    @Schema(description = "盘点药房名称")
    private String purposeLocationName;

    /** 明细行数。 */
    @Schema(description = "明细行数")
    private Integer itemCount;

    /** 盈亏数量合计。 */
    @Schema(description = "盈亏数量合计")
    private BigDecimal profitLossQuantitySum;

    /** 实盘金额合计。 */
    @Schema(description = "实盘金额合计")
    private BigDecimal actualAmountSum;

    /** 申请人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "申请人 applicantId")
    private Long applicantId;

    /** 申请人展示名。 */
    @Schema(description = "申请人姓名")
    private String applicantName;

    /** 申请时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "申请时间 applyTime")
    private LocalDateTime applyTime;

    /** 审批人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "审批人 approverId")
    private Long approverId;

    /** 审批人展示名。 */
    @Schema(description = "审批人姓名")
    private String approverName;

    /** 审批时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "审批时间 approvalTime")
    private LocalDateTime approvalTime;

    /** 制单时间（明细 occurrence_time 组内最大值）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "制单时间 occurrenceTime")
    private LocalDateTime occurrenceTime;
}
