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
 * HerbalMedicationOrderDraftEditedEvent：health-domain-shared 跨领域共享模型。
 */
public class HerbalMedicationOrderDraftEditedEvent extends DomainEvent {
    /** 操作人主键（HerbalMedicationOrderDraftEdited 业务上下文）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public HerbalMedicationOrderDraftEditedEvent() {
        super();
    }

    /**
     * 处理Long id逻辑。
     * @return 返回结果
     */
    public HerbalMedicationOrderDraftEditedEvent(Long id, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_REQUEST);
        this.operatorId = operatorId;
    }
}
