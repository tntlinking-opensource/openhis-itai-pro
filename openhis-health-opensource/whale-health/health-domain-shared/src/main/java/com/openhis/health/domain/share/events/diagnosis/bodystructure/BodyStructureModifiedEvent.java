/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.diagnosis.bodystructure;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 身体部位已修改事件。
 */
@Getter
@Setter
public class BodyStructureModifiedEvent extends DomainEvent {
    /** changedFields（BodyStructureModifiedEvent）。 */
    private List<String> changedFields;

    /**
     * 处理Long bodyStructureId, List<String> changedFields逻辑。
     * @return 返回结果
     */
    public BodyStructureModifiedEvent(Long bodyStructureId, List<String> changedFields) {
        super(bodyStructureId, AggregateTypeConstants.BODY_STRUCTURE);
        this.changedFields = changedFields;
    }
}
