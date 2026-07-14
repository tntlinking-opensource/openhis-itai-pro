/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.registration.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 取消住院登记响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "取消住院登记结果")
public class CancelRegistrationResponse {

    /** 住院就诊 ID（业务字段）。 */
    @Schema(description = "住院就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 取消后状态 code。 */
    @Schema(description = "取消后状态 code（CANCELLED）")
    private String statusEnum;

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;
}
