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
 * 执行人已维护事件。执行人列表变更成功时发布。
 */
@Getter
@Setter
public class ProcedurePerformerEditedEvent extends DomainEvent {
    /** 执行人标识。 */
    private Long performerId;
    /** 执行功能编码。 */
    private String functionCode;
    /** 执行开始时间。 */
    private LocalDateTime startTime;
    /** 执行结束时间。 */
    private LocalDateTime endTime;
    /**
     * 执行人列表 JSON 快照（结构：[{performerId, functionCode, startTime, endTime}]）。
     * 由写模型汇总当前全量执行人后序列化携带，投影器直接写入读模型，无需重新计算。
     */
    private String performerListJson;

    /** 无参构造器（反序列化用）。 */
    public ProcedurePerformerEditedEvent() {
        super();
    }

    /**
     * 向后兼容构造器；performerListJson 通过 setter 注入。
     *
     * @param procedureId  执行记录标识（聚合根 ID）
     * @param performerId  执行人标识
     * @param functionCode 执行功能编码
     * @param startTime    执行开始时间
     * @param endTime      执行结束时间
     */
    public ProcedurePerformerEditedEvent(Long procedureId, Long performerId, String functionCode,
                                         LocalDateTime startTime, LocalDateTime endTime) {
        super(procedureId, AggregateTypeConstants.PROCEDURE);
        this.performerId = performerId;
        this.functionCode = functionCode;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
