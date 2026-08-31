/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检合同对账 — 批次明细行。
 */
@Getter
@Setter
public class ContractReconciliationBatchRowDto {

    private Long batchId;
    private String batchName;
    private Long packageActivityId;
    private String bookingDate;
    private Integer rosterTotal;
    private Integer enrolledCount;
    private Integer unenrolledCount;
    private Integer arrivedCount;
    private Integer pendingArrivalCount;
}
