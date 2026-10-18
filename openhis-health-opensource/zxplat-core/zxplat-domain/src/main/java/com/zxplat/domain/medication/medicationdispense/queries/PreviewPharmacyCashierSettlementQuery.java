/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 药房交账未挂载发药候选预览查询条件（DM-FIN-003 · OP_PHARMACY / IP_PHARMACY）。
 */
@Getter
@Setter
public class PreviewPharmacyCashierSettlementQuery {

    /** 交账类型：{@code OP_PHARMACY} / {@code IP_PHARMACY}。 */
    private String typeEnum;

    /** 发药库房（单药房）；必填。 */
    private Long locationId;

    /** 业务窗起（含）；按 {@code dispense_time} 筛选。 */
    private LocalDateTime dateFrom;

    /** 业务窗止（含）；按 {@code dispense_time} 筛选。 */
    private LocalDateTime dateTo;

    /**
     * 可选发药人软筛（Practitioner 主键）；空或 null 表示不按人过滤。
     * 漏交扫描不使用本字段。
     */
    private List<Long> practitionerIds;

    /**
     * 勾选发药行主键子集；{@code null} 表示不额外收窄（提交挂载由写侧命令解析）。
     */
    private List<Long> selectedDispenseIds;
}
