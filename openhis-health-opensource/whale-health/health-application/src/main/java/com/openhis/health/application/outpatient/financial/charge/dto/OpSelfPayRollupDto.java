/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/** 自费费用汇总（混合结算中非医保部分）。 */
@Data
@Accessors(chain = true)
@Schema(description = "自费费用汇总")
public class OpSelfPayRollupDto {

    /** 自费记账账户主键。 */
    @Schema(description = "自费账户主键")
    private Long accountId;
    /** 自费收费项 id 列表。 */
    @Schema(description = "自费收费项")
    private List<Long> chargeItemIds;
    /** 自费原价合计金额。 */
    @Schema(description = "自费原价合计")
    private BigDecimal totalAmount;
}
