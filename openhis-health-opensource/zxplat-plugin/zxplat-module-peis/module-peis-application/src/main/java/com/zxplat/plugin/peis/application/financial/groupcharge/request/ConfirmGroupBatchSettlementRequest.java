/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.financial.groupcharge.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 团检批量正式结算请求。
 */
@Getter
@Setter
public class ConfirmGroupBatchSettlementRequest {

    /** 名册主键列表。 */
    private List<Long> rosterIds;

    /** 体检中心机构主键（可选，缺省取批次 orgId）。 */
    private Long orgId;
}
