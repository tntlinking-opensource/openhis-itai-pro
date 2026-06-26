/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.outpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/** 混合预结算中的纯自费 chargeItem 汇总。 */
@Data
@Accessors(chain = true)
public class OutpatientChargeSelfPayRollupResult {

    /** 自费项归属账户 id。 */
    private Long accountId;
    /** 自费 chargeItem id 列表。 */
    private List<Long> chargeItemIds;
    /** 自费项原价合计。 */
    private BigDecimal totalAmount;
}
