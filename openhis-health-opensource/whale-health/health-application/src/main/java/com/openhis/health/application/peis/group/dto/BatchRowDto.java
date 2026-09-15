/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 体检分组行 DTO（原批次）。
 */
@Getter
@Setter
public class BatchRowDto {

    private Long batchId;
    private Long companyId;
    private Long contractId;
    private String batchName;
    private Long packageActivityId;
    /** 本组全部可选套餐 AD。 */
    private List<Long> packageActivityIds;
    private LocalDate bookingDate;
    private String statusCode;
    private Long orgId;
}
