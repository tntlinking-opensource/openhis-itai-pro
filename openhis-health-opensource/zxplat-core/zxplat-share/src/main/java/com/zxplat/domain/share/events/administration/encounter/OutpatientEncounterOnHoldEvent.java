/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.encounter;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 门诊暂离事件。
 */
@Getter
@Setter
public class OutpatientEncounterOnHoldEvent extends DomainEvent {

    /** leave原因（OutpatientEncounterOnHoldEvent）。 */
    private String leaveReason;
    /** 暂离后就诊状态枚举 code（与 {@code Encounter.statusEnum} 一致）。 */
    private String statusEnum;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public OutpatientEncounterOnHoldEvent() {
        super();
    }

    /**
     * 处理Long encounterId, String leaveReason逻辑。
     * @return 返回结果
     */
    public OutpatientEncounterOnHoldEvent(Long encounterId, String leaveReason) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.leaveReason = leaveReason;
    }
}
