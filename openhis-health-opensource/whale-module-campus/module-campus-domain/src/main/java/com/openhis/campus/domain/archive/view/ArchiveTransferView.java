/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.archive.view;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class ArchiveTransferView {

    private Long id;
    private String transferType;
    private LocalDate effectiveDate;
    private String fromCampusCode;
    private String toCampusCode;
    private Long fromCollegeId;
    private Long toCollegeId;
    private Long fromDepartmentId;
    private Long toDepartmentId;
    private String reason;
    private String operatorName;
    private LocalDateTime operateTime;
    private String externalRef;
}
