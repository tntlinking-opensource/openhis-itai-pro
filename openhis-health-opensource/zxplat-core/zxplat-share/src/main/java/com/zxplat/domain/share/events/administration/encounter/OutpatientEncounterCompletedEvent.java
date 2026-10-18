/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.encounter;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 门诊完诊事件。
 */
@Getter
@Setter
public class OutpatientEncounterCompletedEvent extends DomainEvent {

    /** 患者主键（OutpatientEncounterCompletedEvent）。 */
    private Long patientId;
    /** end时间（OutpatientEncounterCompletedEvent）。 */
    private LocalDateTime endTime;
    /** 完诊后就诊状态枚举 code（与 {@code Encounter.statusEnum} 一致）。 */
    private String statusEnum;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public OutpatientEncounterCompletedEvent() {
        super();
    }

    /**
     * 处理Long encounterId, Long patientId, LocalDateTime endTime逻辑。
     * @return 返回结果
     */
    public OutpatientEncounterCompletedEvent(Long encounterId, Long patientId, LocalDateTime endTime) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.patientId = patientId;
        this.endTime = endTime;
    }
}
