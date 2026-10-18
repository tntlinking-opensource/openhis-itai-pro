/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.ATDManager.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-NUR-ATD-05：办理出院响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "办理出院响应")
public class ExecuteInpatientDischargeResponse {

    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 就诊主键。 */
    private Long encounterId;

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean succeeded;
}
