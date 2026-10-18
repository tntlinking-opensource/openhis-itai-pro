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
 * 术语映射已创建事件。
 */
@Getter
@Setter
public class TerminologyMappingCreatedEvent extends DomainEvent {
    /**
     * 源术语所在表名。
     */
    private String sourceItemTable;
    /**
     * 源术语编码/编号。
     */
    private String sourceItemNo;
    /**
     * 目标术语编码/编号。
     */
    private String targetItemNo;

    /** 操作人主键（TerminologyMappingCreatedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public TerminologyMappingCreatedEvent() {
        super();
    }

    /**
     * 处理Long id, String sourceItemTable, String sourceItemNo, String targetItemNo逻辑。
     * @return 返回结果
     */
    public TerminologyMappingCreatedEvent(Long id, String sourceItemTable, String sourceItemNo, String targetItemNo, Long operatorId) {
        super(id, AggregateTypeConstants.TERMINOLOGY_MAPPING);
        this.sourceItemTable = sourceItemTable;
        this.sourceItemNo = sourceItemNo;
        this.targetItemNo = targetItemNo;
        this.operatorId = operatorId;
    }
}
