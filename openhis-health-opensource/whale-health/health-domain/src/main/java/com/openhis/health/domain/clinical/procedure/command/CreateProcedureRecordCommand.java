/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.procedure.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 新建执行记录命令。
 */
@Getter
@Setter
public class CreateProcedureRecordCommand {
    /** 请求主键。 */
    private Long requestId;
    /** 请求来源表。 */
    private String requestTable;
    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 类别枚举编码。 */
    private String categoryEnum;
    /** 发生时间。 */
    private LocalDateTime occurrenceTime;
    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 执行科室主键（{@code adm_organization.id} / {@code cln_procedure.org_id}）。
     *
     * <p>医技工作站写入登录 TECH 科，与护士侧（通常留空）隔离（AS-MT-EXE-001 BR-MT-EXE-007）。
     * {@code null} 表示护士站/历史路径，不写医技执行科室。</p>
     */
    private Long orgId;

    /**
     * 为 {@code true} 时跳过「同 requestId+requestTable 已存在则拒绝」门禁，允许多条执行事实（门诊护士站 BR-NUR-005）。
     * <p>{@code null} 或 {@code false} 时保留开立侧幂等保护（BR-PRO-001）。</p>
     */
    private Boolean skipDuplicateRequestAnchorCheck;

    /**
     * 为 {@code true} 时跳过同锚点 occurrence 占用校验。
     * <p>取消执行 CANCEL 对冲建单时使用：被对冲 COMPLETED 行仍占用槽位，对冲完成后由 {@code refund_id} 释放。</p>
     */
    private Boolean skipOccurrenceSlotCheck;

    /**
     * 手术时间（护士站手术执行弹窗；可空）。
     */
    private LocalDateTime procedureTime;

    /**
     * 麻醉开始时间（护士站麻醉执行弹窗；可空）。
     */
    private LocalDateTime anesthesiaStartTime;

    /**
     * 麻醉结束时间（护士站麻醉执行弹窗；可空）。
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
