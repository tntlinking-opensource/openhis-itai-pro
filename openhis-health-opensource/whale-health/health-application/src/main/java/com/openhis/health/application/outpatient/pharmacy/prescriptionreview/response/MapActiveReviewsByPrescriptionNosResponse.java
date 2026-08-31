/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.prescriptionreview.response;

import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.dto.PrescriptionReviewActiveDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * 按处方号批量映射有效点评响应（IF-PRV-07）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "按处方号批量映射有效点评响应")
public class MapActiveReviewsByPrescriptionNosResponse {

    /** 处方号 → 有效点评；仅含有有效点评的键。 */
    @Schema(description = "有效点评 Map")
    private Map<String, PrescriptionReviewActiveDto> reviewsByPrescriptionNo;
}
