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

package com.openhis.health.application.outpatient.pharmacy.returnmedicine.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 退药明细行（IF-PHA-02-03）。
 */
@Data
@Schema(description = "退药明细行")
public class ReturnMedicineDetailRowDto {

    @Schema(description = "发放单标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    @Schema(description = "请求单标识（负向）", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestId;

    @Schema(description = "原药品/耗材请求单标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long refundMedicineId;

    @Schema(description = "药品/耗材定义标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long itemId;

    @Schema(description = "药品/耗材名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemName;

    @Schema(description = "批号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String lotNumber;

    @Schema(description = "应退数量（绝对值）", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal quantity;

    @Schema(description = "单位编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

    @Schema(description = "单位显示", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCodeText;

    @Schema(description = "该行收费合计（按发放单汇总 ChargeItem，与门诊发药展示口径一致）", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal totalPrice;

    @Schema(description = "开单医生姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String doctorName;

    @Schema(description = "退药状态编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refundEnum;

    @Schema(description = "退药状态展示", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refundEnumText;

    @Schema(description = "请求状态编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reqStatus;

    @Schema(description = "请求状态展示", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reqStatusText;

    @Schema(description = "业务表名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String serviceTable;

    @Schema(description = "追溯码（逗号分隔）")
    private String traceNo;
}
