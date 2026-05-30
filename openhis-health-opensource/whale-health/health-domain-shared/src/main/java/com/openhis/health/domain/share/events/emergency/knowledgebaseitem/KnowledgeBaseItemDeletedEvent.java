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

package com.openhis.health.domain.share.events.emergency.knowledgebaseitem;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * DM-EMG-002 §7：知识库条目已逻辑删除。
 */
@Getter
@Setter
public class KnowledgeBaseItemDeletedEvent extends DomainEvent {

    private Long itemId;
    private Long operatorId;

    public KnowledgeBaseItemDeletedEvent() {
        super();
    }

    public KnowledgeBaseItemDeletedEvent(Long itemId, Long operatorId) {
        super(itemId, AggregateTypeConstants.KNOWLEDGE_BASE_ITEM);
        this.itemId = itemId;
        this.operatorId = operatorId;
    }
}
