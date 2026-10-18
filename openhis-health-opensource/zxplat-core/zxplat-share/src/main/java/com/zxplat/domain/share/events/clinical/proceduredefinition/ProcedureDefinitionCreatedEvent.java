/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.clinical.proceduredefinition;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 手术操作定义已创建事件（DM-CLN-009 §5；beforeCommit）。
 */
@Getter
@Setter
public class ProcedureDefinitionCreatedEvent extends DomainEvent {

    /** 目录来源码。 */
    private String sourceEnum;
    /** 目录主编码。 */
    private String procedureCode;
    /** 名称。 */
    private String name;
    /** 发布状态码。 */
    private String statusEnum;

    /**
     * 构造已创建事件。
     *
     * @param id             主键
     * @param sourceEnum     来源码
     * @param procedureCode  主编码
     * @param name           名称
     * @param statusEnum     发布状态
     */
    public ProcedureDefinitionCreatedEvent(
            Long id, String sourceEnum, String procedureCode, String name, String statusEnum) {
        super(id, AggregateTypeConstants.PROCEDURE_DEFINITION);
        this.sourceEnum = sourceEnum;
        this.procedureCode = procedureCode;
        this.name = name;
        this.statusEnum = statusEnum;
    }
}
