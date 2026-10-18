/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 学生公费资格预览。 */
@Data
@Accessors(chain = true)
public class StudentEligibilityPreviewDto {

    private Boolean eligible;
    private String reasonCode;
    private String reasonMessage;
    private Long patientId;
    private Long studentId;
}
