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
 * 执行记录登记不执行事件（Procedure STOPPED）。
 */
@Getter
@Setter
public class ProcedureRecordSkippedEvent extends DomainEvent {
    /** 申请单标识。 */
    private Long requestId;
    /** 申请单来源表名。 */
    private String requestTable;
    /** 执行状态枚举值。 */
    private String statusEnum;
    /** 计划执行时刻。 */
    private LocalDateTime occurrenceTime;
    /** 登记时刻。 */
    private LocalDateTime recordedTime;

    /** 无参构造器（反序列化用）。 */
    public ProcedureRecordSkippedEvent() {
        super();
    }

    public ProcedureRecordSkippedEvent(Long procedureId, Long requestId, String requestTable, String statusEnum) {
        super(procedureId, AggregateTypeConstants.PROCEDURE);
        this.requestId = requestId;
        this.requestTable = requestTable;
        this.statusEnum = statusEnum;
    }
}
