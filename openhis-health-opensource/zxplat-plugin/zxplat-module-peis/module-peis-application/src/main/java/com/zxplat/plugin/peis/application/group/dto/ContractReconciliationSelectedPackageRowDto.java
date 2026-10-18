/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检合同对账 — 分组内按选定套餐汇总。
 */
@Getter
@Setter
public class ContractReconciliationSelectedPackageRowDto {

    private Long packageActivityId;
    private Integer rosterCount;
    private Integer enrolledCount;
    private Integer arrivedCount;
}
