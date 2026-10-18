/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.cashiersettlement.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 交账审批列表筛选条件（DM-FIN-003 §5.2 {@code pageForApproval}）。
 */
@Getter
@Setter
public class PageCashierSettlementApprovalQuery {

    /** 交账类型 code 集合；空表示不限。 */
    private List<String> typeEnums;

    /** 审批状态 code 集合；空表示不限。 */
    private List<String> statusEnums;

    /** 交账提交时间起（含）；可空。 */
    private LocalDateTime submittedFrom;

    /** 交账提交时间止（含）；可空。 */
    private LocalDateTime submittedTo;

    /** 交账科室；可空。 */
    private Long orgId;

    /** 交账药房；可空。 */
    private Long locationId;

    /** 交账单号模糊关键字；可空。 */
    private String busNoKeyword;
}
