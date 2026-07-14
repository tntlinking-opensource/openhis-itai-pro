/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 住院护士站追溯码分配 · 待办患者队列行（**AS-IP-NUR-015 · IF-IP-TRC-01**）。
 */
@Getter
@Setter
public class EncounterPendingTraceAssignmentQueryModel {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键（取自命中发放行）。 */
    private Long patientId;

    /** 仍待分配追溯码的汇总组数量（药+批维度）。 */
    private Integer pendingGroupCount;
}
