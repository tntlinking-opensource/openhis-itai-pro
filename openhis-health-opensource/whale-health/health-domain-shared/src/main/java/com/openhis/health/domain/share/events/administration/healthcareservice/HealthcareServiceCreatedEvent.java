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
 * HealthcareServiceCreatedEvent。
 */
public class HealthcareServiceCreatedEvent extends DomainEvent {
    /**
     * name。
     */
    private String name;
    /**
     * categoryCode。
     */
    private String categoryCode;
    /**
     * typeCode。
     */
    private String typeCode;
    /**
     * offeredOrgId。
     */
    private Long offeredOrgId;
    public HealthcareServiceCreatedEvent() { super(); }
    /**
     * 处理Long id, String name, String categoryCode, String typeCode, Long offeredOrgId逻辑。
     * @return 返回结果
     */
    public HealthcareServiceCreatedEvent(Long id, String name, String categoryCode, String typeCode, Long offeredOrgId) {
        super(id, AggregateTypeConstants.HEALTHCARE_SERVICE);
        this.name = name;
        this.categoryCode = categoryCode;
        this.typeCode = typeCode;
        this.offeredOrgId = offeredOrgId;
    }
}
