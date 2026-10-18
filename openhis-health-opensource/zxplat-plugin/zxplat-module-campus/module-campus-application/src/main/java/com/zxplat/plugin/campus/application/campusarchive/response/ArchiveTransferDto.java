/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusarchive.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class ArchiveTransferDto {

    private Long id;
    private String subjectTypeEnum;
    private Long subjectId;
    private Long patientId;
    private String transferTypeEnum;
    private LocalDate effectiveDate;
    private String fromCampusCode;
    private String toCampusCode;
    private Long fromCollegeId;
    private Long toCollegeId;
    private Long fromDepartmentId;
    private Long toDepartmentId;
    private String reason;
    private String externalRef;
    private String transferSourceEnum;
    private String operatorName;
    private LocalDateTime operateTime;
    private String changeLogBatchId;
}
