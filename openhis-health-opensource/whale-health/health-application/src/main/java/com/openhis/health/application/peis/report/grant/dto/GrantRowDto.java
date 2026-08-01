/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.report.grant.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发放列表行 DTO（IF-PEIS-REP-05）。
 */
@Data
public class GrantRowDto {

    /** 总检报告主键。 */
    private Long diagnosticReportId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 体检流水号。 */
    private String busNo;

    /** 患者姓名。 */
    private String patientName;

    /** 发放状态 code。 */
    private String grantStatusCode;

    /** 报告定稿时间。 */
    private LocalDateTime reportFinalTime;
}
