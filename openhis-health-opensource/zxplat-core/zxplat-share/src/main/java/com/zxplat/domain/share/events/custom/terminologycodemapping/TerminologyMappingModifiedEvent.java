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
 * 术语映射已修改事件。
 */
@Getter
@Setter
public class TerminologyMappingModifiedEvent extends DomainEvent {
    /**
     * 变更字段摘要（逗号分隔或 JSON，口径以写侧实现为准）。
     */
    private String changedFields;

    /** 操作人主键（TerminologyMappingModifiedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public TerminologyMappingModifiedEvent() {
        super();
    }

    /**
     * 处理Long id, String changedFields逻辑。
     * @return 返回结果
     */
    public TerminologyMappingModifiedEvent(Long id, String changedFields, Long operatorId) {
        super(id, AggregateTypeConstants.TERMINOLOGY_MAPPING);
        this.changedFields = changedFields;
        this.operatorId = operatorId;
    }
}
