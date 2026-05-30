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

package com.openhis.health.domain.share.events.custom.terminologycodemapping;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
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
