/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.medication.medicationrequest;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter

/**
 * MedicationOrderVerifiedEvent：health-domain-shared 跨领域共享模型。
 */
public class MedicationOrderVerifiedEvent extends DomainEvent {
    /** performerCheck主键（MedicationOrderVerified 业务上下文）。 */
    private Long performerCheckId;
    private LocalDateTime checkTime;
    /** 持久化状态码（RequestStatus#getCode()，save 后取自写模型）。 */
    private String statusEnum;

    /** 操作人主键（MedicationOrderVerifiedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MedicationOrderVerifiedEvent() {
        super();
    }

    /**
     * 处理Long id, Long performerCheckId, LocalDateTime checkTime逻辑。
     * @return 返回结果
     */
    public MedicationOrderVerifiedEvent(Long id, Long performerCheckId, LocalDateTime checkTime, Long operatorId, String statusEnum) {
        super(id, AggregateTypeConstants.MEDICATION_REQUEST);
        this.performerCheckId = performerCheckId;
        this.checkTime = checkTime;
        this.operatorId = operatorId;
        this.statusEnum = statusEnum;
    }
}
