/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 批量患者详情结果模型。
 */
@Getter
@Setter
/**
 * BatchPatientDetailQueryModel。
 */
public class BatchPatientDetailQueryModel {

    /** 患者标识。 */
    /**
     * patientId。
     */
    private Long patientId;
    /** 患者详情。 */
    /**
     * patientDetail。
     */
    private PatientDetailQueryModel patientDetail;
}
