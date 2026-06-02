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
 * 术语映射已修改事件。
 */
@Getter
@Setter
public class TerminologyMappingModifiedEvent extends DomainEvent {
    /**
     * 变更字段摘要（逗号分隔或 JSON，口径以写侧实现为准）。
     */
    private String changedFields;

    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public TerminologyMappingModifiedEvent() {
        super();
    }

    /**
     * 处理Long id, String changedFields逻辑。
     * @return 返回结果
     */
    public TerminologyMappingModifiedEvent(Long id, String changedFields, Long operatorId) {
        super(id, AggregateTypeConstants.TERMINOLOGY_MAPPING);
        this.changedFields = changedFields;
        this.operatorId = operatorId;
    }
}
