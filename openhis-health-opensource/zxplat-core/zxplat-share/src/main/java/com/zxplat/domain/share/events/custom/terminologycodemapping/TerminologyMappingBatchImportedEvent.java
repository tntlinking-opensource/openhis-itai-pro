/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.custom.terminologycodemapping;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 术语映射批量导入事件。
 */
@Getter
@Setter
public class TerminologyMappingBatchImportedEvent extends DomainEvent {
    /**
     * 源术语所在表名（批量条目共用；可空）。
     */
    private String sourceItemTable;
    /**
     * 新增条数。
     */
    private Integer createdCount;
    /**
     * 更新条数。
     */
    private Integer updatedCount;
    /**
     * 失败条数。
     */
    private Integer failedCount;

    /** 操作人主键（TerminologyMappingBatchImportedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public TerminologyMappingBatchImportedEvent() {
        super();
    }

    public TerminologyMappingBatchImportedEvent(
        Long id,
        String sourceItemTable,
        Integer createdCount,
        Integer updatedCount,
        Integer failedCount
    ) {
        this(id, sourceItemTable, createdCount, updatedCount, failedCount, null);
    }

    public TerminologyMappingBatchImportedEvent(
        Long id,
        String sourceItemTable,
        Integer createdCount,
        Integer updatedCount,
        Integer failedCount,
        Long operatorId
    ) {
        super(id, AggregateTypeConstants.TERMINOLOGY_MAPPING);
        this.sourceItemTable = sourceItemTable;
        this.createdCount = createdCount;
        this.updatedCount = updatedCount;
        this.failedCount = failedCount;
        this.operatorId = operatorId;
    }
}
