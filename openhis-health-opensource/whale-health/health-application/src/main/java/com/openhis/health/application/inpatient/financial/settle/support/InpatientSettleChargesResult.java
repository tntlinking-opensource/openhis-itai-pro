/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.support;

import com.openhis.health.application.inpatient.financial.settle.support.dto.InpatientSettleCategorySummaryDto;
import com.openhis.health.application.inpatient.financial.settle.support.dto.InpatientSettleChargeLineDto;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 住院结算费用读结果（IF-IP-STL-04）。
 */
@Data
@Accessors(chain = true)
public class InpatientSettleChargesResult {

    /** 住院就诊主键。 */
    private Long encounterId;

    /** 费用呈现模式：detail / summary。 */
    private String presentationMode;

    /** 明细行；summary 模式时为 null。 */
    private List<InpatientSettleChargeLineDto> lines;

    /** 大类汇总；detail 模式时为 null。 */
    private List<InpatientSettleCategorySummaryDto> categories;

    /** 待结算 chargeItem 总额。 */
    private BigDecimal pendingSettlementTotal;

    /** 是否默认全选待结算行。 */
    private Boolean defaultSelectAll;
}
