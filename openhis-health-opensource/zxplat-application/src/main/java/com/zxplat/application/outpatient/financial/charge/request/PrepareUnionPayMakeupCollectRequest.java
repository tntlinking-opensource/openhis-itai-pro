/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.charge.request;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 银联补收准备：平台已 2207、支付单已取消、收费项仍待收时，生成可扫码落账的草稿。
 */
@Data
@Accessors(chain = true)
public class PrepareUnionPayMakeupCollectRequest {

    /** 门诊就诊主键。 */
    private Long encounterId;

    /** 待补收收费项 id（须仍为 PLANNED）。 */
    private List<Long> chargeItemIds;
}
