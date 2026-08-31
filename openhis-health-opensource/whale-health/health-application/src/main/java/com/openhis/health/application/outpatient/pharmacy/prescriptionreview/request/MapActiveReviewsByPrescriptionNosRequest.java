/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.prescriptionreview.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 按处方号批量映射有效点评请求（IF-PRV-07）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "按处方号批量映射有效点评请求", requiredProperties = {"prescriptionNos"})
public class MapActiveReviewsByPrescriptionNosRequest {

    /** 处方号列表；空列表返回空 Map。 */
    @Schema(description = "处方号列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> prescriptionNos;
}
