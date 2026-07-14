/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.costdetail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 费用明细展示模式选项（IF-IP-CDT-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "费用明细展示模式选项")
public class NurseStationCostDetailPresentationOptionDto {

    @Schema(description = "展示模式 code")
    /** 展示模式 code。 */
    private String code;

    @Schema(description = "展示模式名称")
    /** 展示模式名称。 */
    private String display;
}
