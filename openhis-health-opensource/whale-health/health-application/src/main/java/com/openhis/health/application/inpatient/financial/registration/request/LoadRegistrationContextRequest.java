/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.registration.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 登记上下文加载请求（IF-IP-REG-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院登记上下文加载请求")
public class LoadRegistrationContextRequest {

    /** 住院就诊 ID；无档登记时为空（业务字段）。 */
    @Schema(description = "住院就诊 ID；无档登记时为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 已选住院科室（预拉费用性质与病区用）。 */
    @Schema(description = "已选住院科室 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;
}
