/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.registration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/** IF-REG-14：可约时段摘要请求。 */
@Data
@Accessors(chain = true)
/** 可约时段摘要请求。 */
@Schema(description = "可约时段摘要请求")
public class ListBookableSlotHintsRequest {

    /** 科室ID。 */
    @Schema(description = "科室ID")
    /** 科室ID（业务字段）。 */
    private Long organizationId;

    /** 医师ID。 */
    @Schema(description = "医师ID")
    /** 医师ID（业务字段）。 */
    private Long practitionerId;

    /** 号源类型ID。 */
    @Schema(description = "号源类型ID")
    /** 号源类型ID（业务字段）。 */
    private Long healthcareServiceId;

    /** 挂号自然日。 */
    @Schema(description = "挂号自然日")
    /** 挂号自然日（业务字段）。 */
    private LocalDate registrationDate;
}
