/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.inpatientadmission.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-REQ-01：发起住院申请响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-住院申请-发起住院申请响应")
public class RequestInpatientAdmissionResponse {

    /** 新建住院就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "新建住院就诊主键")
    private Long inpatientEncounterId;

    /** 住院就诊业务号。 */
    @Schema(description = "住院就诊业务号")
    private String inpatientEncounterBusNo;

    /** 申请是否成功。 */
    @Schema(description = "申请是否成功")
    private boolean success;
}
