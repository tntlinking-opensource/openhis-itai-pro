/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.clinical.proceduredefinition;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 手术操作定义已编辑事件（DM-CLN-009 §5；beforeCommit）。
 */
@Getter
@Setter
public class ProcedureDefinitionEditedEvent extends DomainEvent {

    /** 目录来源码。 */
    private String sourceEnum;
    /** 目录主编码。 */
    private String procedureCode;
    /** 变更字段名列表（最小快照）。 */
    private List<String> changedFields;

    /**
     * 构造已编辑事件。
     *
     * @param id             主键
     * @param sourceEnum     来源码
     * @param procedureCode  主编码
     * @param changedFields  变更字段名
     */
    public ProcedureDefinitionEditedEvent(
            Long id, String sourceEnum, String procedureCode, List<String> changedFields) {
        super(id, AggregateTypeConstants.PROCEDURE_DEFINITION);
        this.sourceEnum = sourceEnum;
        this.procedureCode = procedureCode;
        this.changedFields = changedFields;
    }
}
