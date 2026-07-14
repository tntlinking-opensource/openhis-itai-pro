/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.procedure.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 护士诊疗执行工作量明细分页行（AS-RPT-002 IF-RPT-NWL-03）。
 *
 * <p>GenerateSource 展示名由应用层经 {@code serviceRequestId} 加载 SR 后 enrich，不在本读模型内。</p>
 */
@Getter
@Setter
public class NurseExecutionWorkloadDetailQueryModel {

    /** 执行记录主键 {@code cln_procedure.id}。 */
    private Long procedureId;

    /** 来源 ServiceRequest 主键（= {@code cln_procedure.request_id}）。 */
    private Long serviceRequestId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 主执行人执业者主键（ProcedurePerformer PRIMARY 优先）。 */
    private Long practitionerId;

    /** 诊疗类目（来自 Support 预解析 {@code categoryEnumByServiceRequestId}）。 */
    private String categoryEnum;

    /** 实际执行时刻 {@code recorded_time}（AS 统计时间窗锚点）。 */
    private LocalDateTime recordedTime;
}
