/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.result;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/** 混合预结算单组结果（一个 2203→204→206） */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class OpPreSettleGroupResult extends OpPreSettleResult {

    private Integer groupIndex;
    private String medType;
    private String diseCodg;
    private Long accountId;
    private List<Long> chargeItemIds;
}
