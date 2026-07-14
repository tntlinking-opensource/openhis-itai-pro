/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 公费学生资格校验结果模型。
 */
@Getter
@Setter
public class StudentPublicExpenseEligibilityQueryModel {

    /** 是否通过。 */
    private Boolean eligible;
    /** 原因编码。 */
    private String reasonCode;
    /** 原因说明。 */
    private String reasonMessage;
    /** 患者标识。 */
    private Long patientId;
    /** 学生记录标识。 */
    private Long studentId;
}
