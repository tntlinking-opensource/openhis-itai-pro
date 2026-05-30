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

package com.openhis.health.application.inpatient.financial.settle.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 住院结算费用明细行 DTO（IF-IP-STL-04/07）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院结算费用明细行")
public class InpatientSettleChargeLineDto {

    @Schema(description = "收费明细 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chargeItemId;

    @Schema(description = "项目名称")
    private String itemName;

    @Schema(description = "规格展示")
    private String specText;

    @Schema(description = "费用大类 code")
    private String categoryCode;

    @Schema(description = "费用大类中文")
    private String categoryDisplay;

    @Schema(description = "单价")
    private BigDecimal unitPrice;

    @Schema(description = "数量")
    private BigDecimal quantityValue;

    @Schema(description = "单位")
    private String quantityUnit;

    @Schema(description = "行合计", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal lineAmount;

    @Schema(description = "收费状态 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusEnum;

    @Schema(description = "收费状态展示")
    private String statusEnumText;

    @Schema(description = "业务发生时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime occurrenceTime;

    @Schema(description = "来源可读文案")
    private String sourceText;

    @Schema(description = "是否默认勾选", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean selected;
}
