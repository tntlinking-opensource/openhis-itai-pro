/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 团检合同行 DTO。
 */
@Getter
@Setter
public class ContractRowDto {

    private Long contractId;
    private Long companyId;
    private Long packageActivityId;
    private List<Long> packageActivityIds = new ArrayList<>();
    private String contractName;
    private LocalDate validFrom;
    private LocalDate validTo;
    /** 合同金额（元）。 */
    private BigDecimal contractAmount;
    /** 合同支付状态：UNPAID / PAID。 */
    private String payStatusCode;
    private Long orgId;
}
