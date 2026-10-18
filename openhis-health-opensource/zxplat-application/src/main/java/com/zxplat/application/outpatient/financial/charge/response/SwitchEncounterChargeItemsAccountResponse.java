/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.charge.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊收费账户切换响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊收费账户切换响应")
public class SwitchEncounterChargeItemsAccountResponse {
    /** 是否成功（业务字段）。 */
    @Schema(description = "是否成功")
    private Boolean success;
    /** 结果语义码（业务字段）。 */
    @Schema(description = "结果语义码")
    private String messageCode;
}
