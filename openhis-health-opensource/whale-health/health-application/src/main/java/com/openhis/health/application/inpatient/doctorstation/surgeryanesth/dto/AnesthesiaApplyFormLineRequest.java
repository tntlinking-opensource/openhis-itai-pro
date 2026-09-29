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
 * 保存麻醉申请单行入参（AS-IP-DOR-012 IF-IP-SUR-12）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "麻醉申请单行")
public class AnesthesiaApplyFormLineRequest {

    /** 行种类：ANESTHESIA / SURCHARGE。 */
    @Schema(description = "行种类 ANESTHESIA/SURCHARGE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String lineKindCode;

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

    /** 行备注。 */
    @Schema(description = "行备注")
    private String note;
}
