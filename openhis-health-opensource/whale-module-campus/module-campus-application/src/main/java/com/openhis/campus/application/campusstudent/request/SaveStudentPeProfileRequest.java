/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusstudent.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveStudentPeProfileRequest {

    private Long tenantId;

    @NotNull
    private Long studentId;

    private String cardNo;
    private String medicalCode;
    private String gfStatusEnum;
    private String categoryEnum;
    private Boolean eligibleFlag;
    private String remark;
}
