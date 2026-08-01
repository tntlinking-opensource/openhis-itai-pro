/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.report.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class GradReportRowDto {
    private Long encounterId;
    private String busNo;
    private Long packageActivityId;
    private Boolean groupFlag;
    private String statusEnum;
    private LocalDateTime createdAt;
    /** freshman / annual / staff / other */
    private String examKind;
    private String packageName;
    private Integer calendarYear;
    /** 是否可纳入毕业合打（总检 DR final）。 */
    private Boolean finalReport;
    private Long diagnosticReportId;
}
