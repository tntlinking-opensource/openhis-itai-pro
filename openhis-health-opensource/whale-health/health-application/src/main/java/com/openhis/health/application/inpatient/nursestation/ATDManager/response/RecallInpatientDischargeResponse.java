/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.ATDManager.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-NUR-ATD-07：出院召回响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "出院召回响应")
public class RecallInpatientDischargeResponse {

    /** 就诊主键。 */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean succeeded;

    /** 召回后就诊状态 code。 */
    @Schema(description = "召回后就诊状态 code")
    private String targetStatusEnum;

    /** 是否须二次分床。 */
    @Schema(description = "是否须二次分床")
    private Boolean requiresBedAssignment;
}
