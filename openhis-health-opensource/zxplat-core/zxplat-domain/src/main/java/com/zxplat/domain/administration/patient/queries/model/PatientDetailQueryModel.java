/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.patient.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 患者详情查询模型。
 */
@Getter
@Setter
public class PatientDetailQueryModel {

    /** 患者核心信息。 */
    private PatientCoreQueryModel patient;
    /** 非身份证证件集合。 */
    private List<PatientIdentifierQueryModel> identifiers;
}
