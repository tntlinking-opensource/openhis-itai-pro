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
 * 术语映射已创建事件。
 */
@Getter
@Setter
public class TerminologyMappingCreatedEvent extends DomainEvent {
    /**
     * 源术语所在表名。
     */
    private String sourceItemTable;
    /**
     * 源术语编码/编号。
     */
    private String sourceItemNo;
    /**
     * 目标术语编码/编号。
     */
    private String targetItemNo;

    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public TerminologyMappingCreatedEvent() {
        super();
    }

    /**
     * 处理Long id, String sourceItemTable, String sourceItemNo, String targetItemNo逻辑。
     * @return 返回结果
     */
    public TerminologyMappingCreatedEvent(Long id, String sourceItemTable, String sourceItemNo, String targetItemNo, Long operatorId) {
        super(id, AggregateTypeConstants.TERMINOLOGY_MAPPING);
        this.sourceItemTable = sourceItemTable;
        this.sourceItemNo = sourceItemNo;
        this.targetItemNo = targetItemNo;
        this.operatorId = operatorId;
    }
}
