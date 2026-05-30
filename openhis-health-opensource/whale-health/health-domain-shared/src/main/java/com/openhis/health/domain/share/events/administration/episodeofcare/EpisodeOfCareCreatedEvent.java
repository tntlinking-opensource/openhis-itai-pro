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

package com.openhis.health.domain.share.events.administration.episodeofcare;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * EpisodeOfCareCreatedEvent。
 */
public class EpisodeOfCareCreatedEvent extends DomainEvent {
    /**
     * patientId。
     */
    private Long patientId;
    /**
     * busNo。
     */
    private String busNo;
    /**
     * organizationId。
     */
    private Long organizationId;
    /**
     * statusEnum。
     */
    private String statusEnum;

    public EpisodeOfCareCreatedEvent() { super(); }
    /**
     * 处理Long id, Long patientId, String busNo, Long organizationId, String statusEnum逻辑。
     * @return 返回结果
     */
    public EpisodeOfCareCreatedEvent(Long id, Long patientId, String busNo, Long organizationId, String statusEnum) {
        super(id, AggregateTypeConstants.EPISODE_OF_CARE);
        this.patientId = patientId;
        this.busNo = busNo;
        this.organizationId = organizationId;
        this.statusEnum = statusEnum;
    }
}
