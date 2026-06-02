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
 * 就诊诊断关联维护事件。
 */
@Getter
@Setter
public class EncounterDiagnosisAssociationUpsertedEvent extends DomainEvent {

    private Long conditionId;
    private Boolean primaryFlag;
    private Integer rank;

    /**
     * 就诊维度诊断列表快照 JSON（数组），供门诊信息/挂号读模型投影覆盖 {@code diagnosis_json}。
     */
    private String diagnosisJson;

    /**
     * 主诊断定义名称摘要（挂号列表等展示）；无主诊时取首条诊断定义名称。
     */
    private String conditionDefinitionName;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public EncounterDiagnosisAssociationUpsertedEvent() {
        super();
    }

    /**
     * 处理Long encounterId, Long conditionId, Boolean primaryFlag, Integer rank逻辑。
     * @return 返回结果
     */
    public EncounterDiagnosisAssociationUpsertedEvent(Long encounterId, Long conditionId, Boolean primaryFlag, Integer rank) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.conditionId = conditionId;
        this.primaryFlag = primaryFlag;
        this.rank = rank;
    }
}
