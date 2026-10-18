/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 费用分类净额小计 DTO（IF-IP-CDT-02 合计区）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "费用分类净额小计")
public class NurseStationCostCategoryTotalDto {

    /** 分类 code。 */
    @Schema(description = "分类 code")
    private String categoryCode;

    /** 分类展示名。 */
    @Schema(description = "分类展示名")
    private String categoryDisplay;

    /** 该分类净额合计（BR-IP-CDT-010）。 */
    @Schema(description = "分类净额")
    private BigDecimal amount;
}
