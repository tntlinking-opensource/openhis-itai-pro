/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.events.clinical.procedure;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
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
