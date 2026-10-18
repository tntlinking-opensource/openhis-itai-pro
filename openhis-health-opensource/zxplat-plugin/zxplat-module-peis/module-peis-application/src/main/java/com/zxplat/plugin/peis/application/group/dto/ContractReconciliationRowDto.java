/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 团检合同对账汇总行。
 */
@Getter
@Setter
public class ContractReconciliationRowDto {

    private Long contractId;
    private String contractName;
    private Long companyId;
    private String companyName;
    private BigDecimal contractAmount;
    private String payStatusCode;
    private LocalDate validFrom;
    private LocalDate validTo;
    private Integer batchCount;
    private Integer rosterTotal;
    private Integer enrolledCount;
    private Integer unenrolledCount;
    private Integer arrivedCount;
    private Integer pendingArrivalCount;
    /** 加项待收笔数（PLANNED，锚定 SR）。 */
    private Integer addOnPendingCount;
    /** 加项待收金额。 */
    private BigDecimal addOnPendingAmount;
    /** 加项已收金额（SETTLED，锚定 SR）。 */
    private BigDecimal addOnSettledAmount;
    private List<ContractReconciliationBatchRowDto> batches = new ArrayList<>();
}
