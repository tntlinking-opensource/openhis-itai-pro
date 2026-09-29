/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.procedure.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 执行记录查询模型。
 */
@Getter
@Setter
public class ProcedureRecordQueryModel {
    /** 主键。 */
    private Long id;
    /** 请求主键。 */
    private Long requestId;
    /** 请求来源表。 */
    private String requestTable;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** 记录时间。 */
    private LocalDateTime recordedTime;
    /**
     * 取消执行关联标识：仅 {@code CANCEL} 行有意义，指向被抵消的已完成执行记录主键（{@code cln_procedure.id}）。
     */
    private Long refundId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 执行分类枚举编码。 */
    private String categoryEnum;
    /** 发生时间。 */
    private LocalDateTime occurrenceTime;
    /** 记录人主键。 */
    private Long recorderId;
    /** 结果摘要。 */
    private String resultText;
    /**
     * 执行科室主键（{@code cln_procedure.org_id}）。
     *
     * <p>医技侧为登录 TECH 科；护士站历史行多为 {@code null}，用于双侧同态与次数隔离。</p>
     */
    private Long orgId;

    /**
     * 手术时间（护士站手术执行弹窗）。
     */
    private LocalDateTime procedureTime;

    /**
     * 麻醉开始时间（护士站麻醉执行弹窗）。
     */
    private LocalDateTime anesthesiaStartTime;

    /**
     * 麻醉结束时间（护士站麻醉执行弹窗）。
     */
    private LocalDateTime anesthesiaEndTime;

    /**
     * 切口类别（术中可填）。
     */
    private String incisionTypeCode;

    /**
     * 是否送检（术中可填）。
     */
    private Boolean sendLabFlag;

    /**
     * 样本（护士可填；与备注分列）。
     */
    private String specimen;

    /**
     * 备注（护士可填；与样本分列）。
     */
    private String note;
}
