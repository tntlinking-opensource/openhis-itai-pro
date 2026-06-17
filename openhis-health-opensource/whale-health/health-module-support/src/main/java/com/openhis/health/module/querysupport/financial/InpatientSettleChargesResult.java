/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.querysupport.financial;

import com.openhis.health.module.querysupport.financial.dto.InpatientSettleCategorySummaryDto;
import com.openhis.health.module.querysupport.financial.dto.InpatientSettleChargeLineDto;
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

    private Long encounterId;
    private String presentationMode;
    private List<InpatientSettleChargeLineDto> lines;
    private List<InpatientSettleCategorySummaryDto> categories;
    private BigDecimal pendingSettlementTotal;
    private Boolean defaultSelectAll;
}
