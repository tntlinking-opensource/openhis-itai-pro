/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.prescriptionreview.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 点评记录页初始化响应（IF-PRV-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "点评记录页初始化响应")
public class LoadPageInitResponse {

    /** 点评状态下拉（PENDING / REVIEWED）。 */
    @Schema(description = "点评状态下拉")
    private List<OptionDto> statusOptions;

    /** 是否合理下拉（合理 / 不合理）。 */
    @Schema(description = "是否合理下拉")
    private List<OptionDto> reasonableOptions;

    /** 默认点评日起（yyyy-MM-dd，本月首日）。 */
    @Schema(description = "默认点评日起")
    private String defaultReviewedDateFrom;

    /** 默认点评日止（yyyy-MM-dd，本月末日）。 */
    @Schema(description = "默认点评日止")
    private String defaultReviewedDateTo;
}
