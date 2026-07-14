/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.response;

import com.openhis.health.application.outpatient.financial.registration.dto.RegistrationSlipDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 补打挂号单响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "补打挂号单响应")
public class PrintRegisterTicketResponse {

    /** 挂号单（业务字段）。 */
    @Schema(description = "挂号单")
    private RegistrationSlipDto slip;

    /** 打印后累计次数。 */
    @Schema(description = "打印后次数")
    private Integer printCount;
}
