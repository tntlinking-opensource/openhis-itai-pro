/*
 * Copyright © 2026 OpenHIS Contributors
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
/** 可约时段摘要响应。 */
@Schema(description = "可约时段摘要响应")
public class ListBookableSlotHintsResponse {

    /** 可约时段列表。 */
    @Schema(description = "可约时段列表")
    /** 可约时段列表（业务字段）。 */
    private List<BookableSlotHintDto> hints;
}
