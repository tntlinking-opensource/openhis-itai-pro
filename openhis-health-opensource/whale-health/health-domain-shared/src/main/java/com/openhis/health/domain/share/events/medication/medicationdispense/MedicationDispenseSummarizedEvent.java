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

import java.util.List;

/**
 * 门诊/住院汇总发药：多条发放单绑定同一汇总单号后发布，供读模型投影汇总头与明细关联。
 */
@Getter
@Setter
public class MedicationDispenseSummarizedEvent extends DomainEvent {
    /** 汇总发药业务单号。 */
    private String summaryNo;
    /** 纳入汇总的发放单主键列表。 */
    private List<Long> ids;

    /** 汇总操作人用户主键。 */
    private Long operatorId;

    /**
     * 汇总发药单头持久化状态码（与 {@link com.openhis.health.domain.share.enums.MedicationSummaryStatus#getCode()} 一致），供读模型投影，禁止投影侧自拟。
     */
    private String summaryHeaderStatusEnumCode;

    /** 反序列化用无参构造。 */
    public MedicationDispenseSummarizedEvent() {
        super();
    }

    /**
     * 汇总绑定：生成汇总单号并绑定多条发放单明细。
     *
     * @param summaryNo 汇总单号
     * @param ids 纳入汇总的发放单主键列表
     * @param operatorId 操作人
     */
    public MedicationDispenseSummarizedEvent(String summaryNo, List<Long> ids, Long operatorId) {
        super(resolveAggregateId(ids), AggregateTypeConstants.MEDICATION_DISPENSE);
        this.summaryNo = summaryNo;
        this.ids = ids;
        this.operatorId = operatorId;
    }

    /**
     * 事件聚合标识：以首条发放单主键代表本批汇总。
     *
     * @param ids 发放单主键列表
     * @return 聚合 id；列表为空时为 null
     */
    private static Long resolveAggregateId(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.get(0);
    }
}
