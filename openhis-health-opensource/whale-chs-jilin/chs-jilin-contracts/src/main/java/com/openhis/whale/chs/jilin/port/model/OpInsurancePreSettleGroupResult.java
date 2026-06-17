/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/** 医保预结算分组结果（单组 2203→2206）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class OpInsurancePreSettleGroupResult extends OpInsurancePreSettleResult {

    private Integer groupIndex;
    private String medType;
    private String diseCodg;
    private Long accountId;
    private List<Long> chargeItemIds;
}
