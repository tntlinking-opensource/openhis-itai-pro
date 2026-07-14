/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissuereturn.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 科室退领单写模型明细行（IF-DEP-TL-04/05）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室退领单明细行（写）")
public class DeptIssueReturnLineDto {

    /** 物品来源表名 itemTable。 */
    @Schema(description = "物品来源表名 itemTable", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemTable;

    /** 物品定义主键 itemId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "物品定义主键 itemId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long itemId;

    /** 批号 lotNumber。 */
    @Schema(description = "批号 lotNumber")
    private String lotNumber;

    /** 追溯码 traceNo。 */
    @Schema(description = "追溯码 traceNo")
    private String traceNo;

    /** 退回数量 itemQuantity。 */
    @Schema(description = "退回数量 itemQuantity", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal itemQuantity;

    /** 计量单位编码 unitCode。 */
    @Schema(description = "计量单位编码 unitCode", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

    /** 单价 price。 */
    @Schema(description = "单价 price")
    private BigDecimal price;
}
