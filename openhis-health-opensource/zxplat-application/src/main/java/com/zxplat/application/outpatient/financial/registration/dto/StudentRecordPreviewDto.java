/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 学籍记录预览行。 */
@Data
@Accessors(chain = true)
public class StudentRecordPreviewDto {

    private Long studentId;
    private String name;
    private String studentNo;
    private String college;
    private String major;
    private String grade;
    private Integer schoolStatus;
    private Integer physicalExamResult;
    private String leaveTimeText;
}
