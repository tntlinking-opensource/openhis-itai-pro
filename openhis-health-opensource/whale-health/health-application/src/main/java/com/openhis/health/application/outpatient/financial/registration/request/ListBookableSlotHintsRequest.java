/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/** IF-REG-14：可约时段摘要请求。 */
@Data
@Accessors(chain = true)
@Schema(description = "可约时段摘要请求")
public class ListBookableSlotHintsRequest {

    /** 科室ID。 */
    @Schema(description = "科室ID")
    private Long organizationId;

    /** 医师ID。 */
    @Schema(description = "医师ID")
    private Long practitionerId;

    /** 号源类型ID。 */
    @Schema(description = "号源类型ID")
    private Long healthcareServiceId;

    /** 挂号自然日。 */
    @Schema(description = "挂号自然日")
    private LocalDate registrationDate;
}
