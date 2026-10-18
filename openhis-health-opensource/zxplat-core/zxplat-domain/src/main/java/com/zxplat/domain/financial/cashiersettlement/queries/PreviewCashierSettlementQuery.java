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
 * 未交账 payment 预览查询条件。
 */
@Getter
@Setter
public class PreviewCashierSettlementQuery {

    /**
     * 交账类型：{@code OP_PAYMENT} / {@code IP_PAYMENT}。
     */
    private String typeEnum;

    /**
     * 收款日结科室主键（落库交账头 {@code org_id}；Payment 读侧 org-location 范围锚点，必填）。
     */
    private Long orgId;

    /**
     * 可选 location 白名单（收款日结应传 {@code null}：不绑款台、payment 无科室列，禁止用「科室下地点」误过滤）。
     * 非 null 时 Payment QS 按 {@code location_id IN locationIds} 过滤；空列表表示无地点、结果为空。
     */
    private List<Long> locationIds;

    /**
     * payment {@code enterer_id} 匹配白名单（Practitioner 主键 + 绑定用户主键）；可选软筛，空则不按人过滤。
     */
    private List<Long> entererMatchIds;

    /** 日结时间起（含）。 */
    private LocalDateTime dateFrom;

    /** 日结时间止（含）。 */
    private LocalDateTime dateTo;
}
