/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.patient.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 同一人重复建档校验结果模型。
 */
@Getter
@Setter
public class DuplicatePatientCheckQueryModel {

    /** 是否重复。 */
    private Boolean duplicated;
    /** 命中患者集合。 */
    private List<PatientSimpleQueryModel> matchedPatients;
    /** 命中规则列表。 */
    private List<String> ruleHits;
}
