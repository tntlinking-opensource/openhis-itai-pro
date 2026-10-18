/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusarchive.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PublicExpenseProfileDto {

    private Long id;
    private String subjectTypeEnum;
    private Long subjectId;
    private Long patientId;
    private String cardNo;
    private String medicalCode;
    private String gfStatusEnum;
    private String categoryEnum;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private Boolean eligibleFlag;
    private String remark;
}
