/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-ARG-03：新增自动计费配置响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "新增自动计费配置响应")
public class CreateAutoGenRuleResponse {

    /** 规则主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "规则主键")
    private Long ruleId;
}
