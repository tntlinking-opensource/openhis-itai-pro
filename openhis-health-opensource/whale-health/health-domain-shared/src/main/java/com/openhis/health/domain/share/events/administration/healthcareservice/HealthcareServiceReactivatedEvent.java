/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.healthcareservice;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;

/**
 * HealthcareServiceReactivatedEvent。
 */
public class HealthcareServiceReactivatedEvent extends DomainEvent {
    public HealthcareServiceReactivatedEvent() { super(); }
    /**
     * 处理Long id逻辑。
     * @return 返回结果
     */
    public HealthcareServiceReactivatedEvent(Long id) {
        super(id, AggregateTypeConstants.HEALTHCARE_SERVICE);
    }
}
