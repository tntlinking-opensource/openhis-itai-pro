/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检合同对账 — 分组（batchId）明细行。
 */
@Getter
@Setter
public class ContractReconciliationBatchRowDto {

    private Long batchId;
    private String batchName;
    /** 兼容列：分组第一项可选套餐。 */
    private Long packageActivityId;
    /** 本组全部可选套餐 AD。 */
    private List<Long> optionalPackageActivityIds;
    /** 名册尚未选定套餐的人数。 */
    private Integer unselectedCount;
    /** 按 {@code selected_package_activity_id} 汇总。 */
    private List<ContractReconciliationSelectedPackageRowDto> selectedPackages;
    private String bookingDate;
    private Integer rosterTotal;
    private Integer enrolledCount;
    private Integer unenrolledCount;
    private Integer arrivedCount;
    private Integer pendingArrivalCount;
}
