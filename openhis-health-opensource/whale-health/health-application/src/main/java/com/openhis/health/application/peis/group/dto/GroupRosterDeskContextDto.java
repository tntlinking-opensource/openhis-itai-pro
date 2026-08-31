/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.dto;

import lombok.Data;

/**
 * 登记台团检名册上下文（按人）。
 */
@Data
public class GroupRosterDeskContextDto {

    private Long rosterId;
    private Long batchId;
    private Long patientId;
    private String fullName;
    private Long companyId;
    private String companyName;
    private String batchName;
    private Long packageActivityId;
    private String packageName;
    private Long encounterId;
    private String payStatusCode;
    private String arrivalStatusCode;
    /** 是否已完成建单（含免收费 ENROLLED 与收费 PAID）。 */
    private Boolean enrolled;
    /** 是否已到检。 */
    private Boolean arrived;
}
