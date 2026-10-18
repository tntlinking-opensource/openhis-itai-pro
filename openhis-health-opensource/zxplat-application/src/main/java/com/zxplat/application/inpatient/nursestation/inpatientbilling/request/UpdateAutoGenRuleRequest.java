/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * IF-IP-ARG-04：更新自动计费配置请求（非空字段覆盖；不含启停）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "更新自动计费配置请求")
public class UpdateAutoGenRuleRequest {

    /** 规则主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "规则主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long ruleId;

    /** 住院就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "住院就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 规则名称。 */
    @Schema(description = "规则名称")
    private String ruleName;

    /** 诊疗活动定义主键（自动计费选品；排除护理/转科/出院/嘱托）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊疗活动定义主键")
    private Long activityId;

    /** 收费项定价主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费项定价主键")
    private Long chargeDefinitionId;

    /** 生成数量。 */
    @Schema(description = "生成数量")
    private BigDecimal quantityValue;

    /** 执行科室主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "执行科室主键")
    private Long performingOrgId;

    /** 优先级。 */
    @Schema(description = "优先级")
    private Integer priority;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /**
     * 滚费业务时刻（非空则覆盖）。
     */
    @Schema(description = "滚费业务时刻 HH:mm:ss")
    private LocalTime businessTime;

    /**
     * 滚费计费人（非空则覆盖）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "滚费计费人")
    private Long billingPractitionerId;
}
