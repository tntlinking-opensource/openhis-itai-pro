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

package com.openhis.health.domain.share.events.administration.organization;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrganizationRetiredEvent extends DomainEvent {
    private String retireReason;
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public OrganizationRetiredEvent() {
        super();
    }

    /**
     * 处理Long organizationId, String retireReason, Long operatorId逻辑。
     * @return 返回结果
     */
    public OrganizationRetiredEvent(Long organizationId, String retireReason, Long operatorId) {
        super(organizationId, AggregateTypeConstants.ORGANIZATION);
        this.retireReason = retireReason;
        this.operatorId = operatorId;
    }
}
