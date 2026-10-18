/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.custom.terminologycodemapping;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 术语映射已恢复事件。
 */
@Getter
@Setter
public class TerminologyMappingReactivatedEvent extends DomainEvent {

    /** 操作人主键（TerminologyMappingReactivatedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public TerminologyMappingReactivatedEvent() {
        super();
    }

    /**
     * 处理Long id逻辑。
     * @return 返回结果
     */
    public TerminologyMappingReactivatedEvent(Long id, Long operatorId) {
        super(id, AggregateTypeConstants.TERMINOLOGY_MAPPING);
        this.operatorId = operatorId;
    }
}
