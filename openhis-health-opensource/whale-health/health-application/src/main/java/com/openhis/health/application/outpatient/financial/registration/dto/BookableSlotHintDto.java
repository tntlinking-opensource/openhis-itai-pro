/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** IF-REG-14：可约时段摘要行。 */
@Data
@Accessors(chain = true)
/** 可约时段摘要。 */
@Schema(description = "可约时段摘要")
public class BookableSlotHintDto {

    /** 号源ID。 */
    @Schema(description = "号源ID")
    /** 号源ID（业务字段）。 */
    private Long slotId;

    /** 开始。 */
    @Schema(description = "开始")
    /** 开始（业务字段）。 */
    private LocalDateTime slotStart;

    /** 结束。 */
    @Schema(description = "结束")
    /** 结束（业务字段）。 */
    private LocalDateTime slotEnd;

    /** 剩余可约数。 */
    @Schema(description = "剩余可约数")
    /** 剩余可约数（业务字段）。 */
    private Integer remainingCount;
}
