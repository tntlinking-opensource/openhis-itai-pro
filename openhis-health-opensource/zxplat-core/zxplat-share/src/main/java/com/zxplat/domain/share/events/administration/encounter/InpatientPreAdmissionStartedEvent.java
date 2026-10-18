/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.encounter;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 住院预入院开始事件。
 */
@Getter
@Setter
public class InpatientPreAdmissionStartedEvent extends DomainEvent {

    /** preAdmissionItems（InpatientPreAdmissionStartedEvent）。 */
    private List<String> preAdmissionItems;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public InpatientPreAdmissionStartedEvent() {
        super();
    }

    /**
     * 处理Long encounterId, List<String> preAdmissionItems逻辑。
     * @return 返回结果
     */
    public InpatientPreAdmissionStartedEvent(Long encounterId, List<String> preAdmissionItems) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.preAdmissionItems = preAdmissionItems;
    }
}
