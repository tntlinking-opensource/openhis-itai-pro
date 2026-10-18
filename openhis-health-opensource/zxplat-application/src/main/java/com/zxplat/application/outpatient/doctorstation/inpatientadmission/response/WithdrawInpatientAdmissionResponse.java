/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.inpatientadmission.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-REQ-03：医生站撤回待登记住院申请响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-住院申请-撤回申请响应")
public class WithdrawInpatientAdmissionResponse {

    /** 被撤回（软删）的住院就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "被撤回的住院就诊主键")
    private Long inpatientEncounterId;

    /** 撤回前住院就诊状态码（通常为 PLANNED）。 */
    @Schema(description = "撤回前住院就诊状态码")
    private String statusEnum;

    /** 撤回是否成功。 */
    @Schema(description = "撤回是否成功")
    private boolean success;
}
