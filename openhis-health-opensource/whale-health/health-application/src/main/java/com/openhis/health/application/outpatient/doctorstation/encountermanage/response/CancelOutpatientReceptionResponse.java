/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.encountermanage.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 医生站门诊取消接诊响应（IF-DOR-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-门诊取消接诊响应")
public class CancelOutpatientReceptionResponse {

    /** 就诊标识。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识")
    private Long encounterId;

    /** 领域命令是否已成功执行。 */
    @Schema(description = "领域命令是否已成功执行")
    private Boolean success;
}
