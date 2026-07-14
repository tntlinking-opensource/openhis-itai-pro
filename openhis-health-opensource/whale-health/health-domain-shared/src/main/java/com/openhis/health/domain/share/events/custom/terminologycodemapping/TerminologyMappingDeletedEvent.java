/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.terminologycodemapping;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 术语映射已删除事件。
 */
@Getter
@Setter
public class TerminologyMappingDeletedEvent extends DomainEvent {
    /**
     * 源术语所在表名。
     */
    private String sourceItemTable;
    /**
     * 源术语编码/编号。
     */
    private String sourceItemNo;

    /** 操作人主键（TerminologyMappingDeletedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public TerminologyMappingDeletedEvent() {
        super();
    }

    /**
     * 处理Long id, String sourceItemTable, String sourceItemNo逻辑。
     * @return 返回结果
     */
    public TerminologyMappingDeletedEvent(Long id, String sourceItemTable, String sourceItemNo, Long operatorId) {
        super(id, AggregateTypeConstants.TERMINOLOGY_MAPPING);
        this.sourceItemTable = sourceItemTable;
        this.sourceItemNo = sourceItemNo;
        this.operatorId = operatorId;
    }
}
