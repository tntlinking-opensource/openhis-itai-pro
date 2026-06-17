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

package com.openhis.health.domain.share.events.custom.frequency;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 频次已创建事件。
 */
@Getter
@Setter
public class FrequencyCreatedEvent extends DomainEvent {
    /**
     * 频次编码。
     */
    private String rateCode;
    /**
     * 频次名称。
     */
    private String name;
    /**
     * 周期类型 code。
     */
    private String cycleType;

    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public FrequencyCreatedEvent() {
        super();
    }

    /**
     * 处理Long id, String rateCode, String name, Integer cycleType逻辑。
     * @return 返回结果
     */
    public FrequencyCreatedEvent(Long id, String rateCode, String name, String cycleType, Long operatorId) {
        super(id, AggregateTypeConstants.FREQUENCY);
        this.rateCode = rateCode;
        this.name = name;
        this.cycleType = cycleType;
        this.operatorId = operatorId;
    }
}
