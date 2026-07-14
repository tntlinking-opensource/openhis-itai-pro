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
     * 为 {@code true} 时跳过「同 requestId+requestTable 已存在则拒绝」门禁，允许多条执行事实（门诊护士站 BR-NUR-005）。
     * <p>{@code null} 或 {@code false} 时保留开立侧幂等保护（BR-PRO-001）。</p>
     */
    private Boolean skipDuplicateRequestAnchorCheck;

    /**
     * 为 {@code true} 时跳过同锚点 occurrence 占用校验。
     * <p>取消执行 CANCEL 对冲建单时使用：被对冲 COMPLETED 行仍占用槽位，对冲完成后由 {@code refund_id} 释放。</p>
     */
    private Boolean skipOccurrenceSlotCheck;
}
