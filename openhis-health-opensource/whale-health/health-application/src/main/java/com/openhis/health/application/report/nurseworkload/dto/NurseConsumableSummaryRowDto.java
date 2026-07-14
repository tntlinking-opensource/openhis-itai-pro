/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.nurseworkload.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 护士耗材使用汇总行（IF-RPT-NWL-04）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "护士耗材使用汇总行")
public class NurseConsumableSummaryRowDto {

    /** 高值标志。 */
    @Schema(description = "高值标志")
    private Boolean hvcmFlag;

    /** 高/低值展示名。 */
    @Schema(description = "高/低值展示名")
    private String hvcmDisplay;

    /** 数量合计。 */
    @Schema(description = "数量合计")
    private BigDecimal quantityTotal;
}
