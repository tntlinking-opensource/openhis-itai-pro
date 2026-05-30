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

package com.openhis.health.domain.share.events.diagnosis.bodystructure;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 身体部位已删除事件。
 */
@Getter
@Setter
public class BodyStructureDeletedEvent extends DomainEvent {
    /** 本次软删的身体部位主键列表（批量删除同批发布）。 */
    private List<Long> ids;

    /**
     * 身体部位批量删除后发布；聚合根 id 取列表首项供事件路由。
     *
     * @param ids 已删除部位主键列表，可空
     */
    public BodyStructureDeletedEvent(List<Long> ids) {
        super(resolveAggregateId(ids), AggregateTypeConstants.BODY_STRUCTURE);
        this.ids = ids;
    }

    /**
     * 事件聚合标识：多行删除时以首条主键代表本批。
     *
     * @param ids 部位主键列表
     * @return 聚合 id；列表为空时为 null
     */
    private static Long resolveAggregateId(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.get(0);
    }
}
