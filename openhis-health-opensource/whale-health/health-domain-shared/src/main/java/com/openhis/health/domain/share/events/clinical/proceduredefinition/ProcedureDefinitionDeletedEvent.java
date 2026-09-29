/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.clinical.proceduredefinition;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 手术操作定义已软删事件（DM-CLN-009 §5；beforeCommit）。
 */
@Getter
@Setter
public class ProcedureDefinitionDeletedEvent extends DomainEvent {

    /** 目录来源码。 */
    private String sourceEnum;
    /** 目录主编码。 */
    private String procedureCode;

    /**
     * 构造已删除事件。
     *
     * @param id            主键
     * @param sourceEnum    来源码
     * @param procedureCode 主编码
     */
    public ProcedureDefinitionDeletedEvent(Long id, String sourceEnum, String procedureCode) {
        super(id, AggregateTypeConstants.PROCEDURE_DEFINITION);
        this.sourceEnum = sourceEnum;
        this.procedureCode = procedureCode;
    }
}
