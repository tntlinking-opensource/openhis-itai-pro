/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

    /** 患者主键（NurseStationPatientSavedEvent）。 */
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
