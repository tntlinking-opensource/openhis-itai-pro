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
 * 取消住院登记请求（IF-IP-REG-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "取消住院登记请求")
public class CancelRegistrationRequest {

    /** 住院就诊 ID（业务字段）。 */
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 取消原因。 */
    @Schema(description = "取消原因")
    private String cancelReason;

    /** 操作人 ID。 */
    @Schema(description = "操作人 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;
}
