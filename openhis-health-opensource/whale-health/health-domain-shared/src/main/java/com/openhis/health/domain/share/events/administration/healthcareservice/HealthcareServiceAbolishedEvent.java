/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.healthcareservice;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * HealthcareServiceAbolishedEvent。
 */
public class HealthcareServiceAbolishedEvent extends DomainEvent {
    /**
     * reason。
     */
    private String reason;
    public HealthcareServiceAbolishedEvent() { super(); }
    /**
     * 处理Long id, String reason逻辑。
     * @return 返回结果
     */
    public HealthcareServiceAbolishedEvent(Long id, String reason) {
        super(id, AggregateTypeConstants.HEALTHCARE_SERVICE);
        this.reason = reason;
    }
}
