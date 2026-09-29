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
 * 麻醉申请单行（AS-IP-DOR-012 IF-IP-SUR-11）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "麻醉申请单行")
public class AnesthesiaApplyFormLineDto {

    /** 服务申请主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "服务申请主键")
    private Long serviceRequestId;

    /** 行种类 ANESTHESIA/SURCHARGE。 */
    @Schema(description = "行种类")
    private String lineKindCode;

    /** 计费诊疗项目主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "计费诊疗项目主键")
    private Long activityDefinitionId;

    /** 诊疗项目名称。 */
    @Schema(description = "诊疗项目名称")
    private String activityName;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 单位编码。 */
    @Schema(description = "单位编码")
    private String unitCode;

    /** 行状态。 */
    @Schema(description = "行状态")
    private String statusEnum;

    /** 排序号。 */
    @Schema(description = "排序号")
    private Integer sortNumber;
}
