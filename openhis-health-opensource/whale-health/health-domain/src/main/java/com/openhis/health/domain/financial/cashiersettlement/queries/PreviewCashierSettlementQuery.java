/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.cashiersettlement.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 未交账 payment 预览查询条件。
 */
@Getter
@Setter
public class PreviewCashierSettlementQuery {

    /** 交账类型：OUTPATIENT / INPATIENT。 */
    private String typeEnum;

    /** 经办人（enterer 过滤）。 */
    private Long cashierId;

    /**
     * payment {@code enterer_id} 匹配白名单（Practitioner 主键 + 绑定用户主键）；由应用层写入。
     */
    private List<Long> entererMatchIds;

    /** 业务日起。 */
    private LocalDate dateFrom;

    /** 业务日止。 */
    private LocalDate dateTo;
}
