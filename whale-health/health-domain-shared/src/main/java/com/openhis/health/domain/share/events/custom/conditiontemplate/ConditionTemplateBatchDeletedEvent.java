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

package com.openhis.health.domain.share.events.custom.conditiontemplate;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 诊断模板批量已删除事件。
 */
@Getter
@Setter
public class ConditionTemplateBatchDeletedEvent extends DomainEvent {
    private List<Long> templateIds;

    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public ConditionTemplateBatchDeletedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, List<Long> templateIds逻辑。
     * @return 返回结果
     */
    public ConditionTemplateBatchDeletedEvent(Long aggregateId, List<Long> templateIds, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.CONDITION_TEMPLATE);
        this.templateIds = templateIds;
        this.operatorId = operatorId;
    }
}
