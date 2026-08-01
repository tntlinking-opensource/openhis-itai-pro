/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 团检合同行 DTO。
 */
@Getter
@Setter
public class ContractRowDto {

    private Long contractId;
    private Long companyId;
    private Long packageActivityId;
    private String contractName;
    private LocalDate validFrom;
    private LocalDate validTo;
    private Long orgId;
}
