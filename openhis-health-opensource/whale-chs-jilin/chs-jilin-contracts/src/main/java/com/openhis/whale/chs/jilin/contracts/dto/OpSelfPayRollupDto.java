/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/** 混合预结算中的自费费用汇总（不调医保平台）。 */
@Data
@Accessors(chain = true)
public class OpSelfPayRollupDto {

    /** 自费项归属账户 id。 */
    private Long accountId;
    /** 自费 chargeItem id 列表。 */
    private List<Long> chargeItemIds;
    /** 自费项原价合计（元）。 */
    private BigDecimal totalAmount;
}
