/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * IF-IP-ARG-03：新增自动计费配置请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "新增自动计费配置请求")
public class CreateAutoGenRuleRequest {

    /** 住院就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "住院就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 规则名称。 */
    @Schema(description = "规则名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ruleName;

    /** 床位服务活动定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "床位服务活动定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long activityId;

    /** 收费项定价主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费项定价主键", requiredMode = Schema.RequiredMode.REQUIRED)
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
}
