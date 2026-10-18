/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.prepay.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 预交金流水查询请求（IF-IP-PREP-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "预交金流水查询请求")
public class ListPrepayPaymentRecordsRequest {

    /** 住院就诊 ID。 */
    @Schema(description = "住院就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者 ID（与就诊联校，可选）。 */
    @Schema(description = "患者 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;
}
