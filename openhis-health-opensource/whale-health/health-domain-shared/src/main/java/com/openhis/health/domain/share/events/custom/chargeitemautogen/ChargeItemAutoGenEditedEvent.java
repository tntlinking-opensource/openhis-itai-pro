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

package com.openhis.health.domain.share.events.custom.chargeitemautogen;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 自动计费规则已变更事件。
 */
@Getter
@Setter
public class ChargeItemAutoGenEditedEvent extends DomainEvent {
    /**
     * 操作类型（创建/更新/删除/状态变更等；取值与 CustomConstants.ChargeItemAutoGenOperation 对齐）。
     */
    private String operation;
    /**
     * 触发来源表名。
     */
    private String triggerSourceTable;
    /**
     * 触发来源主键。
     */
    private Long triggerSourceId;
    /**
     * 目标收费项定义主键。
     */
    private Long chargeDefinitionId;

    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public ChargeItemAutoGenEditedEvent() {
        super();
    }

    public ChargeItemAutoGenEditedEvent(
        Long ruleId,
        String operation,
        String triggerSourceTable,
        Long triggerSourceId,
        Long chargeDefinitionId
    ) {
        this(ruleId, operation, triggerSourceTable, triggerSourceId, chargeDefinitionId, null);
    }

    /**
     * @param ruleId 聚合主键（= 表 {@code cus_charge_item_auto_gen.id}，落 {@link DomainEvent#aggregateId}）
     */
    public ChargeItemAutoGenEditedEvent(
        Long ruleId,
        String operation,
        String triggerSourceTable,
        Long triggerSourceId,
        Long chargeDefinitionId,
        Long operatorId
    ) {
        super(ruleId, AggregateTypeConstants.CHARGE_ITEM_AUTO_GEN);
        this.operation = operation;
        this.triggerSourceTable = triggerSourceTable;
        this.triggerSourceId = triggerSourceId;
        this.chargeDefinitionId = chargeDefinitionId;
        this.operatorId = operatorId;
    }
}
