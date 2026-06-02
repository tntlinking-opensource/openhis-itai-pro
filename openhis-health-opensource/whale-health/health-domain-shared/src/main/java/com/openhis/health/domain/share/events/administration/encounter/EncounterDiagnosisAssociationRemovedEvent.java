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
 * 就诊诊断关联移除事件。
 */
@Getter
@Setter
public class EncounterDiagnosisAssociationRemovedEvent extends DomainEvent {

    private Long conditionId;

    /**
     * 删除后的就诊维度诊断列表快照 JSON（数组），供读模型覆盖 {@code diagnosis_json}。
     */
    private String diagnosisJson;

    /**
     * 主诊断定义名称摘要；无记录时可为空。
     */
    private String conditionDefinitionName;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public EncounterDiagnosisAssociationRemovedEvent() {
        super();
    }

    /**
     * 处理Long encounterId, Long conditionId逻辑。
     * @return 返回结果
     */
    public EncounterDiagnosisAssociationRemovedEvent(Long encounterId, Long conditionId) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.conditionId = conditionId;
    }
}
