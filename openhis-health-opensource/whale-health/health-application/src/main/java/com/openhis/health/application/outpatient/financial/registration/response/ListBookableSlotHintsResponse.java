/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.response;

import com.openhis.health.application.outpatient.financial.registration.dto.BookableSlotHintDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** IF-REG-14：可约时段摘要响应。 */
@Data
@Accessors(chain = true)
@Schema(description = "可约时段摘要响应")
public class ListBookableSlotHintsResponse {

    /** 可约时段列表。 */
    @Schema(description = "可约时段列表")
    private List<BookableSlotHintDto> hints;
}
