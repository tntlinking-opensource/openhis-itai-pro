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
 * 术语映射已停用事件。
 */
@Getter
@Setter
public class TerminologyMappingDeactivatedEvent extends DomainEvent {
    /**
     * 停用原因（可空）。
     */
    private String reason;

    /** 操作人主键（TerminologyMappingDeactivatedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public TerminologyMappingDeactivatedEvent() {
        super();
    }

    /**
     * 处理Long id, String reason逻辑。
     * @return 返回结果
     */
    public TerminologyMappingDeactivatedEvent(Long id, String reason, Long operatorId) {
        super(id, AggregateTypeConstants.TERMINOLOGY_MAPPING);
        this.reason = reason;
        this.operatorId = operatorId;
    }
}
