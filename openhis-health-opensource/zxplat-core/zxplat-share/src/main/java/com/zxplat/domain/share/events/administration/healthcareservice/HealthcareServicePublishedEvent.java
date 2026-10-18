/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.healthcareservice;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * HealthcareServicePublishedEvent。
 */
public class HealthcareServicePublishedEvent extends DomainEvent {
    /**
     * name。
     */
    private String name;
    public HealthcareServicePublishedEvent() { super(); }
    /**
     * 处理Long id, String name逻辑。
     * @return 返回结果
     */
    public HealthcareServicePublishedEvent(Long id, String name) {
        super(id, AggregateTypeConstants.HEALTHCARE_SERVICE);
        this.name = name;
    }
}
