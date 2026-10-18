/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.encounter;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 转科确认事件。
 */
@Getter
@Setter
public class DepartmentTransferConfirmedEvent extends DomainEvent {

    /** targetDepartment主键（DepartmentTransferConfirmedEvent）。 */
    private Long targetDepartmentId;
    /** effectiveAt（DepartmentTransferConfirmedEvent）。 */
    private LocalDateTime effectiveAt;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public DepartmentTransferConfirmedEvent() {
        super();
    }

    /**
     * 处理Long encounterId, Long targetDepartmentId, LocalDateTime effectiveAt逻辑。
     * @return 返回结果
     */
    public DepartmentTransferConfirmedEvent(Long encounterId, Long targetDepartmentId, LocalDateTime effectiveAt) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.targetDepartmentId = targetDepartmentId;
        this.effectiveAt = effectiveAt;
    }
}
