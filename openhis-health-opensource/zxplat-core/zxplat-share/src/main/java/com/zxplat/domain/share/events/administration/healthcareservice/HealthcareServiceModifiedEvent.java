/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.healthcareservice;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
/**
 * HealthcareServiceModifiedEvent。
 */
public class HealthcareServiceModifiedEvent extends DomainEvent {
    /**
     * changedFields。
     */
    private List<String> changedFields;
    public HealthcareServiceModifiedEvent() { super(); }
    /**
     * 处理Long id, List<String> changedFields逻辑。
     * @return 返回结果
     */
    public HealthcareServiceModifiedEvent(Long id, List<String> changedFields) {
        super(id, AggregateTypeConstants.HEALTHCARE_SERVICE);
        this.changedFields = changedFields;
    }
}
