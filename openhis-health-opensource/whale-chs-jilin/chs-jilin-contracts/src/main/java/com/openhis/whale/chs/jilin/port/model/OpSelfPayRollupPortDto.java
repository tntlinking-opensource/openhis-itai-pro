/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/** 自费费用汇总（不进医保平台）。 */
@Data
@Accessors(chain = true)
public class OpSelfPayRollupPortDto {

    private Long accountId;
    private List<Long> chargeItemIds;
    private BigDecimal totalAmount;
}
