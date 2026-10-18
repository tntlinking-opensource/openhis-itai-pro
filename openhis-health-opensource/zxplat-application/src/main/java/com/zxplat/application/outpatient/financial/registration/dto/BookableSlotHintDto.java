/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** IF-REG-14：可约时段摘要行。 */
@Data
@Accessors(chain = true)
@Schema(description = "可约时段摘要")
public class BookableSlotHintDto {

    /** 号源ID。 */
    @Schema(description = "号源ID")
    private Long slotId;

    /** 开始。 */
    @Schema(description = "开始")
    private LocalDateTime slotStart;

    /** 结束。 */
    @Schema(description = "结束")
    private LocalDateTime slotEnd;

    /** 剩余可约数。 */
    @Schema(description = "剩余可约数")
    private Integer remainingCount;
}
