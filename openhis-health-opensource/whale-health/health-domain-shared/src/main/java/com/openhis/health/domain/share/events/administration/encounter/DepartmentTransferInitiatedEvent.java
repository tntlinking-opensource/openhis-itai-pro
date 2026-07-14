/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.encounter;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 发起转科事件。
 */
@Getter
@Setter
public class DepartmentTransferInitiatedEvent extends DomainEvent {

    /** fromDepartment主键（DepartmentTransferInitiatedEvent）。 */
    private Long fromDepartmentId;
    /** toDepartment主键（DepartmentTransferInitiatedEvent）。 */
    private Long toDepartmentId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public DepartmentTransferInitiatedEvent() {
        super();
    }

    /**
     * 处理Long encounterId, Long fromDepartmentId, Long toDepartmentId逻辑。
     * @return 返回结果
     */
    public DepartmentTransferInitiatedEvent(Long encounterId, Long fromDepartmentId, Long toDepartmentId) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.fromDepartmentId = fromDepartmentId;
        this.toDepartmentId = toDepartmentId;
    }
}
