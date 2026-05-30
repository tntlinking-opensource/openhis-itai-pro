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

package com.openhis.health.domain.share.events.administration.encounter;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 发起转科事件。
 */
@Getter
@Setter
public class DepartmentTransferInitiatedEvent extends DomainEvent {

    private Long fromDepartmentId;
    private Long toDepartmentId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public DepartmentTransferInitiatedEvent() {
        super();
    }

    /**
     * 处理Long encounterId, Long fromDepartmentId, Long toDepartmentId逻辑。
     * @return 返回结果
     */
    public DepartmentTransferInitiatedEvent(Long encounterId, Long fromDepartmentId, Long toDepartmentId) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.fromDepartmentId = fromDepartmentId;
        this.toDepartmentId = toDepartmentId;
    }
}
