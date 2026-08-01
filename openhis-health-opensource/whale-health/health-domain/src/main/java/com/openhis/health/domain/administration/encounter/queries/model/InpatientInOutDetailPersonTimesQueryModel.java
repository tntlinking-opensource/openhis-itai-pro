/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 住院患者入出明细报表人次页脚（AS-RPT-010；按 Encounter 计，不去重患者）。
 */
@Getter
@Setter
public class InpatientInOutDetailPersonTimesQueryModel {

    /** 窗内入院人次（start_time ∈ 窗）。 */
    private Long admissionPersonTimes;

    /** 窗内出院人次（end_time ∈ 窗）。 */
    private Long dischargePersonTimes;
}
