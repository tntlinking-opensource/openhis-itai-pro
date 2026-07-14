/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.refund.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 半退保留行账户切换响应（IF-REF-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "半退保留行账户切换响应")
public class SwitchPreservedChargeItemsAccountResponse {

    /** 是否切换成功（业务字段）。 */
    @Schema(description = "是否切换成功")
    private boolean success;
}
