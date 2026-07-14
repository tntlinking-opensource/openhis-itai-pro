/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 患者学籍合并视图模型。
 */
@Getter
@Setter
public class PatientStudentProfileQueryModel {

    /** 患者标识。 */
    private Long patientId;
    /** 姓名。 */
    private String name;
    /** 身份证号。 */
    private String idCard;
    /** 联系电话。 */
    private String phone;
    /** 学籍记录列表。 */
    private List<PatientStudentQueryModel> studentRecords;
}
