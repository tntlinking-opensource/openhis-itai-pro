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
 * 手术操作目录批量导入完成事件（DM-CLN-009 §5；beforeCommit）。
 */
@Getter
@Setter
public class ProcedureDefinitionCatalogImportedEvent extends DomainEvent {

    /** 导入来源码（clinical_proc_v3 / general_op）。 */
    private String sourceEnum;
    /** 实际写入条数。 */
    private int writtenCount;

    /**
     * 构造目录已导入事件（无单一聚合根 id，aggregateId 为空）。
     *
     * @param sourceEnum   来源码
     * @param writtenCount 写入条数
     */
    public ProcedureDefinitionCatalogImportedEvent(String sourceEnum, int writtenCount) {
        super(null, AggregateTypeConstants.PROCEDURE_DEFINITION);
        this.sourceEnum = sourceEnum;
        this.writtenCount = writtenCount;
    }
}
