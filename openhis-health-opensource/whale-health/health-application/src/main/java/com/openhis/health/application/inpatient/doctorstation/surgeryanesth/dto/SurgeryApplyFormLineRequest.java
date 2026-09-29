/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.surgeryanesth.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 保存手术申请单行入参（AS-IP-DOR-012 IF-IP-SUR-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "手术申请单行")
public class SurgeryApplyFormLineRequest {

    /** 行种类：PROCEDURE / SURCHARGE。 */
    @Schema(description = "行种类 PROCEDURE/SURCHARGE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String lineKindCode;

    /** 国临手术定义主键（术式行必填）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "国临手术定义主键")
    private Long procedureDefinitionId;

    /** 计费诊疗项目主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "计费诊疗项目主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long activityDefinitionId;

    /** 开立数量。 */
    @Schema(description = "开立数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal quantity;

    /** 计量单位编码。 */
    @Schema(description = "计量单位编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

    /** 排序号。 */
    @Schema(description = "排序号")
    private Integer sortNumber;

    /** 主手术标志。 */
    @Schema(description = "主手术标志")
    private Boolean mainProcedureFlag;

    /** 行备注。 */
    @Schema(description = "行备注")
    private String note;
}
