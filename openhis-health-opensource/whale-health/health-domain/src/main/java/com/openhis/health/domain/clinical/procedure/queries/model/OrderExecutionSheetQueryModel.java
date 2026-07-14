/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.procedure.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 医嘱执行单查询行（仅 {@code cln_procedure} 主表；展示列由应用层 enrich 批量回填）。
 */
@Getter
@Setter
public class OrderExecutionSheetQueryModel {

    /** 执行记录主键。 */
    private Long procedureId;

    /** 来源请求主键。 */
    private Long requestId;

    /** 来源表持久化名。 */
    private String requestTable;

    /** 执行状态持久化码。 */
    private String statusEnum;

    /** 执行分类持久化码。 */
    private String categoryEnum;

    /** 患者主键。 */
    private Long patientId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 发生时间。 */
    private LocalDateTime occurrenceTime;

    /** 记录时间。 */
    private LocalDateTime recordedTime;

    /** 记录人主键。 */
    private Long recorderId;

    /** 结果摘要。 */
    private String resultText;
}
