/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dispense.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 作废门诊发放响应（IF-OPP-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "作废门诊发放响应")
public class CancelOutpatientMedicationDispenseResponse {

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "提示文案")
    private String message;

    /** 本次作废涉及的发放单主键集合。 */
    @Schema(description = "已处理发放单主键集合")
    private List<Long> processedDispenseIds;
}
