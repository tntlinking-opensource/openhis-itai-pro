/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.result;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/** 混合预结算单组结果（一次 2203→2204→2206）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class OpInsurancePreSettleGroupResult extends OpInsurancePreSettleResult {

    /** 组序号（1-based，收银 UI 展示）。 */
    private Integer groupIndex;
    /** 本组医疗类别。 */
    private String medType;
    /** 慢病病种编码 dise_codg。 */
    private String diseCodg;
    /** 本组费用账户 id。 */
    private Long accountId;
    /** 本组包含的 chargeItem id 列表。 */
    private List<Long> chargeItemIds;
}
