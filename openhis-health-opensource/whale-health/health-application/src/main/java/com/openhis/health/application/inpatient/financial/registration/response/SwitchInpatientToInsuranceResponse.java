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
 * 住院自费转医保响应（IF-IP-REG-07）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院自费转医保响应")
public class SwitchInpatientToInsuranceResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 医保就诊 ID（2401 成功时）。 */
    @Schema(description = "医保就诊 ID（2401 成功时）")
    private String ybMdtrtId;

    /** 本次切换至医保账户的收费行数。 */
    @Schema(description = "本次切换至医保账户的收费行数")
    private Integer switchedChargeItemCount;

    /** 新开或复用的医保挂账账户 ID。 */
    @Schema(description = "医保挂账账户 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long insuranceAccountId;

    /** 语义码（成功时可为空）。 */
    @Schema(description = "语义码")
    private String messageCode;
}
