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
 * 更正入院科室时按病区列出可选床（IF-IP-REG-08 读侧）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "入院科室变更可选床请求")
public class ListAdmissionDepartmentBedsRequest {

    /** 目标病区主键。 */
    @Schema(description = "病区 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long wardId;

    /**
     * 当前住院就诊主键。传入时，若其活动床已在该病区下，一并返回，便于只改科室、不换床。
     */
    @Schema(description = "当前住院就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;
}
