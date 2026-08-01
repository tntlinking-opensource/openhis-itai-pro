/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.activityobservationlink;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 项目 AD ↔ 小项 OD 绑定集已变更事件（DM-CUS-013 saveLinks 后发布）。
 */
@Getter
@Setter
public class ActivityObservationLinksChangedEvent extends DomainEvent {

    /** 项目 ActivityDefinition 主键。 */
    private Long activityDefinitionId;

    /** 对齐后有效（未软删）链接行数。 */
    private int linkCount;

    /**
     * 构造绑定变更事件。
     *
     * @param activityDefinitionId 项目 AD 主键
     * @param linkCount            有效链接数
     */
    public ActivityObservationLinksChangedEvent(Long activityDefinitionId, int linkCount) {
        super(activityDefinitionId, AggregateTypeConstants.ACTIVITY_OBSERVATION_LINK);
        this.activityDefinitionId = activityDefinitionId;
        this.linkCount = linkCount;
    }
}
