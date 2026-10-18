/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.encountermanage.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 医生站门诊取消接诊请求（IF-DOR-06）：医生同意退号，将就诊回退待诊。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-门诊取消接诊请求")
public class CancelOutpatientReceptionRequest {

    /** 门诊就诊标识。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "门诊就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 操作人主键。 */
    @NotNull(message = "操作人不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "操作人主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long operatorId;
}
