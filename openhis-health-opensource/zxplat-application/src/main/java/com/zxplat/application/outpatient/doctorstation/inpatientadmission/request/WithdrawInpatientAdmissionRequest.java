/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.inpatientadmission.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-REQ-03：医生站撤回待登记住院申请。
 *
 * <p>仅 {@code PLANNED} 可撤回；直接软删住院线（不迁取消态），款台登记队列不可见，
 * 医生可再次「办理住院」编辑并重新提交。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-住院申请-撤回申请请求")
public class WithdrawInpatientAdmissionRequest {

    /** 源门诊就诊主键（定位未取消住院线）。 */
    @NotNull(message = "门诊就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "源门诊就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long ambEncounterId;

    /** 操作人主键（医师/当前登录人）。 */
    @NotNull(message = "操作人不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "操作人主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long operatorId;
}
