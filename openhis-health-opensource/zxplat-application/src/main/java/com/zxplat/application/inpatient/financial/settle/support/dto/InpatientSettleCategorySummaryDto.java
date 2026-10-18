/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.settle.support.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 住院结算费用大类汇总 DTO（IF-IP-STL-04/07）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院结算费用大类汇总")
public class InpatientSettleCategorySummaryDto {

    /** 费用大类 code（产品类目；无法解析时为 {@code _UNKNOWN}）。 */
    @Schema(description = "大类 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String categoryCode;

    /** 费用大类展示名。 */
    @Schema(description = "大类名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String categoryDisplay;

    /** 该大类待结算金额合计。 */
    @Schema(description = "该大类待结算金额合计", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;

    /** 归入该大类的明细条数。 */
    @Schema(description = "明细条数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer lineCount;
}
