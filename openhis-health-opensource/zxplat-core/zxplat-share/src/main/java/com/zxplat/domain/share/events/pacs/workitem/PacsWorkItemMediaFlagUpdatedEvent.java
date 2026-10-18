/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.pacs.workitem;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 检查工单媒体标记已更新事件。
 */
@Getter
@Setter
public class PacsWorkItemMediaFlagUpdatedEvent extends DomainEvent {

    /** 是否已有影像/关键图。 */
    private Boolean hasMedia;

    /** 无参构造器（反序列化用）。 */
    public PacsWorkItemMediaFlagUpdatedEvent() {
        super();
    }

    /**
     * @param workItemId 工单主键（聚合根 ID）
     * @param hasMedia 媒体标记
     */
    public PacsWorkItemMediaFlagUpdatedEvent(Long workItemId, Boolean hasMedia) {
        super(workItemId, AggregateTypeConstants.PACS_WORK_ITEM);
        this.hasMedia = hasMedia;
    }
}
