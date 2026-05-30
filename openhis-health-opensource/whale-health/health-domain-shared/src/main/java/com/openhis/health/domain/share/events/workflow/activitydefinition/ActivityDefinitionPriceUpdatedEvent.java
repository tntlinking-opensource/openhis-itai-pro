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

package com.openhis.health.domain.share.events.workflow.activitydefinition;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 诊疗项目定价已更新事件。
 *
 * <p>SDD-WKF-001 §7 最小载荷：activityDefinitionId（via super.aggregateId）、
 * oldStandardPrice、newStandardPrice、reasonCode。</p>
 */
@Getter
@Setter
public class ActivityDefinitionPriceUpdatedEvent extends DomainEvent {

    /** 变更前标准价。 */
    private BigDecimal oldStandardPrice;

    /** 变更后标准价。 */
    private BigDecimal newStandardPrice;

    /** 变更原因代码。 */
    private String reasonCode;

    /**
     * 默认构造函数（反序列化用）。
     */
    public ActivityDefinitionPriceUpdatedEvent() {
        super();
    }

    /**
     * 构造诊疗项目定价已更新事件。
     *
     * @param activityDefinitionId 诊疗项目主键（aggregateId）
     * @param oldStandardPrice     变更前标准价
     * @param newStandardPrice     变更后标准价
     * @param reasonCode           变更原因代码
     */
    public ActivityDefinitionPriceUpdatedEvent(Long activityDefinitionId, BigDecimal oldStandardPrice,
                                               BigDecimal newStandardPrice, String reasonCode) {
        super(activityDefinitionId, AggregateTypeConstants.ACTIVITY_DEFINITION);
        this.oldStandardPrice = oldStandardPrice;
        this.newStandardPrice = newStandardPrice;
        this.reasonCode = reasonCode;
    }
}
