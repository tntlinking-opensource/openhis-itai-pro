/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 住院护士站追溯码分配 · 患者维度药+批汇总行（**AS-IP-NUR-015 · IF-IP-TRC-02**）。
 */
@Getter
@Setter
public class PatientTraceAssignmentAggregateLineQueryModel {

    /** 稳定组键：{@code summaryNo:medicationId:lotNumber}。 */
    private String groupKey;

    /** 汇总单号。 */
    private String summaryNo;

    /** 药品定义主键。 */
    private Long medicationId;

    /** 批号。 */
    private String lotNumber;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 组内底层发放行主键集合。 */
    private List<Long> dispenseIds;

    /** 组内底层发放行数量明细。 */
    private List<PatientTraceAssignmentDispenseLineQueryModel> dispenseLines;

    /** 组级已分配追溯码（组内首条非空；全空则为 null）。 */
    private String assignedTraceNo;

    /** 是否仍有底层行 trace 为空（{@code true} 表示待分配）。 */
    private Boolean assignmentPending;
}
