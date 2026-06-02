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

package com.openhis.health.domain.share.events.medication.medicationdispense;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MedicationDispenseSummaryCancelledEvent extends DomainEvent {
    private String summaryNo;

    private Long operatorId;

    /** 取消汇总后发放单持久化状态码（与写模型一致）。 */
    private String slipStatusEnumCode;

    /**
     * 汇总发药单头持久化状态码（与 {@link com.openhis.health.domain.share.enums.MedicationSummaryStatus#getCode()} 一致），供读模型投影。
     */
    private String summaryHeaderStatusEnumCode;

    /** 反序列化用无参构造。 */
    public MedicationDispenseSummaryCancelledEvent() {
        super();
    }

    /**
     * 取消汇总：指定发放单明细解绑汇总单。
     *
     * @param id 发放单主键（聚合锚点）
     * @param summaryNo 原汇总单号
     * @param operatorId 操作人
     */
    public MedicationDispenseSummaryCancelledEvent(Long id, String summaryNo, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_DISPENSE);
        this.summaryNo = summaryNo;
        this.operatorId = operatorId;
    }
}
