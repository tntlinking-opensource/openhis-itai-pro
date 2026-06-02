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

package com.openhis.health.application.inpatient.nursestation.costdetail.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 费用明细行 DTO（IF-IP-CDT-02，presentationMode=DETAIL）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院护士站费用明细行")
public class NurseStationCostDetailLineDto {

    /** 收费明细主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费明细主键")
    private Long chargeItemId;

    /** 展示用产品 id（组套子项取父项 product_id，BR-IP-CDT-006）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "展示用产品 id")
    private Long productId;

    /** 展示用产品表名。 */
    @Schema(description = "展示用产品表名")
    private String productTableName;

    /** 项目名称。 */
    @Schema(description = "项目名称")
    private String itemName;

    /** 规格展示。 */
    @Schema(description = "规格展示")
    private String itemSpec;

    /** 账单数量（原样）。 */
    @Schema(description = "账单数量")
    private BigDecimal quantityValue;

    /** 账单单位 code。 */
    @Schema(description = "账单单位 code")
    private String quantityUnit;

    /** 账单单位展示名。 */
    @Schema(description = "账单单位展示名")
    private String quantityUnitDisplay;

    /** 账单单价（原样，BR-IP-CDT-012）。 */
    @Schema(description = "账单单价")
    private BigDecimal unitPrice;

    /** 账单行合计（原样；退费负金额保留）。 */
    @Schema(description = "账单行合计")
    private BigDecimal totalPrice;

    /** 业务时间（划价/净 Procedure 执行时刻）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "业务时间")
    private LocalDateTime businessDateTime;

    /** 费用状态 code。 */
    @Schema(description = "费用状态 code")
    private String statusEnum;

    /** 费用状态展示名。 */
    @Schema(description = "费用状态展示名")
    private String statusDisplay;

    /** 生成来源 code。 */
    @Schema(description = "生成来源 code")
    private String generateSourceEnum;

    /** 生成来源展示名。 */
    @Schema(description = "生成来源展示名")
    private String generateSourceDisplay;

    /** 退费醒目标识（REFUNDING/REFUNDED 为 true，BR-IP-CDT-013）。 */
    @Schema(description = "退费醒目标识")
    private Boolean refundHighlight;

    /** 上游业务表（费用大类映射源）。 */
    @Schema(description = "上游业务表")
    private String serviceTable;

    /** 费用大类块 code：MEDICATION/SERVICE/DEVICE。 */
    @Schema(description = "费用大类块 code")
    private String expenseBlockCode;
}
