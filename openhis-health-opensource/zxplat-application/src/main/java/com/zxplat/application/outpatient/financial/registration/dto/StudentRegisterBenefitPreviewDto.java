/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 学生公费挂号待遇预览。 */
@Data
@Accessors(chain = true)
public class StudentRegisterBenefitPreviewDto {

    private Long patientId;
    private String patientName;
    private String idCard;
    private String phone;
    private List<StudentRecordPreviewDto> studentRecords = new ArrayList<>();
}
