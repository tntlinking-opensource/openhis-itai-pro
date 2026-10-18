/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.location;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 位置信息已变更事件。
 */
@Getter
@Setter
public class LocationInfoEditedEvent extends DomainEvent {
    /** changedFields（LocationInfoEditedEvent）。 */
    private List<String> changedFields;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public LocationInfoEditedEvent() {
        super();
    }

    /**
     * 处理Long locationId, List<String> changedFields逻辑。
     * @return 返回结果
     */
    public LocationInfoEditedEvent(Long locationId, List<String> changedFields) {
        super(locationId, AggregateTypeConstants.LOCATION);
        this.changedFields = changedFields;
    }
}
