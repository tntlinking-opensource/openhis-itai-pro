/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 更正入院科室请求（IF-IP-REG-08）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "更正入院科室请求")
public class ChangeAdmissionDepartmentRequest {

    /** 住院就诊主键。 */
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 目标住院科室主键。 */
    @Schema(description = "目标住院科室 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;

    /** 目标病区主键，须挂在目标科室下。 */
    @Schema(description = "目标病区 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long wardId;

    /**
     * 目标床位主键。已入科必填，须属于目标病区；待入科不传。
     */
    @Schema(description = "目标床位 ID；已入科必填")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bedId;

    /** 操作人主键；空则取当前登录人。 */
    @Schema(description = "操作人 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;
}
