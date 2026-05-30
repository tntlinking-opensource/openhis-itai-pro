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

import java.time.LocalDateTime;

/**
 * 住院护士站患者信息已保存事件：病情优先级与参与者轨迹变更；不含床位与状态迁移。
 */
@Getter
@Setter
public class NurseStationPatientSavedEvent extends DomainEvent {

    private Long patientId;
    /** 保存后病情优先级编码。 */
    private String priorityEnum;
    /** 保存后就诊状态（与写模型一致，未迁移）。 */
    private String statusEnum;
    /** 保存生效时刻。 */
    private LocalDateTime savedAt;
    /** 操作人主键。 */
    private Long operatorId;

    public NurseStationPatientSavedEvent() {
        super();
    }

    public NurseStationPatientSavedEvent(Long encounterId, Long patientId) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.patientId = patientId;
    }
}
