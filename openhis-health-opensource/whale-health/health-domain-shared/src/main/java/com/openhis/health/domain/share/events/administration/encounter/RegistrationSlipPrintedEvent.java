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
 * 挂号单已打印事件。打印挂号单成功时发布；用于读模型更新打印次数及获取挂号单号。
 */
@Getter
@Setter
public class RegistrationSlipPrintedEvent extends DomainEvent {

    /** 就诊标识（用于定位挂号单读模型）。 */
    private Long encounterId;

    /** 挂号单号（由写模型生成后携带，读模型直接赋值，无需投影器自行拼接）。 */
    private String slipNo;

    /** 无参构造器（反序列化用）。 */
    public RegistrationSlipPrintedEvent() {
        super();
    }

    /**
     * 全参构造器。
     *
     * @param aggregateId 聚合根 ID（通常等于 encounterId）
     * @param encounterId 就诊标识
     * @param slipNo      挂号单号
     */
    public RegistrationSlipPrintedEvent(Long aggregateId, Long encounterId, String slipNo) {
        super(aggregateId, AggregateTypeConstants.ENCOUNTER);
        this.encounterId = encounterId;
        this.slipNo = slipNo;
    }
}
