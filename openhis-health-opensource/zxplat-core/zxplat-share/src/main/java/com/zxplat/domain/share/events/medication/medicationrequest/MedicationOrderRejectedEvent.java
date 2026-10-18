/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.medication.medicationrequest;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

/**
 * MedicationOrderRejectedEvent：health-domain-shared 跨领域共享模型。
 */
public class MedicationOrderRejectedEvent extends DomainEvent {
    /** 原因（MedicationOrderRejected 业务上下文）。 */
    private String reason;
    /** 持久化状态码（RequestStatus#getCode()，save 后取自写模型）。 */
    private String statusEnum;

    /** 操作人主键（MedicationOrderRejectedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MedicationOrderRejectedEvent() {
        super();
    }

    /**
     * 处理Long id, String reason逻辑。
     * @return 返回结果
     */
    public MedicationOrderRejectedEvent(Long id, String reason, Long operatorId, String statusEnum) {
        super(id, AggregateTypeConstants.MEDICATION_REQUEST);
        this.reason = reason;
        this.operatorId = operatorId;
        this.statusEnum = statusEnum;
    }
}
