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
