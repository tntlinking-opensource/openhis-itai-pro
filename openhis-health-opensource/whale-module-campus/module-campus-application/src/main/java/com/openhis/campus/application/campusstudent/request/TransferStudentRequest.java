/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusstudent.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class TransferStudentRequest {

    private Long tenantId;

    @NotNull
    private Long studentId;

    @NotNull
    private Long toCollegeId;

    @NotBlank
    private String toCampusCode;

    private LocalDate effectiveDate;
    private String reason;
    private String externalRef;
}
