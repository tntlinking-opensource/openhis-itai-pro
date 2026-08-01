/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 团检批次行 DTO。
 */
@Getter
@Setter
public class BatchRowDto {

    private Long batchId;
    private Long companyId;
    private Long contractId;
    private String batchName;
    private Long packageActivityId;
    private LocalDate bookingDate;
    private String statusCode;
    private Long orgId;
}
