/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.clinical.procedure;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 执行记录已完成事件。执行完成时发布。
 */
@Getter
@Setter
public class ProcedureRecordCompletedEvent extends DomainEvent {
    /** 执行状态枚举值（完成态）。 */
    private String statusEnum;
    /** 执行结果文本。 */
    private String resultText;
    /** 报告摘要（短文本）。 */
    private String reportSummary;
    /** 实际执行时间。 */
    private LocalDateTime occurrenceTime;
    /** 报告完整 JSON（结构化检查/治疗结果，供读模型直接存储）。 */
    private String reportJson;

    /** 无参构造器（反序列化用）。 */
    public ProcedureRecordCompletedEvent() {
        super();
    }

    /**
     * 向后兼容构造器；occurrenceTime / reportJson 通过 setter 注入。
     *
     * @param procedureId   执行记录标识（聚合根 ID）
     * @param statusEnum    执行状态枚举值
     * @param resultText    执行结果文本
     * @param reportSummary 报告摘要
     */
    public ProcedureRecordCompletedEvent(Long procedureId, String statusEnum, String resultText, String reportSummary) {
        super(procedureId, AggregateTypeConstants.PROCEDURE);
        this.statusEnum = statusEnum;
        this.resultText = resultText;
        this.reportSummary = reportSummary;
    }
}
