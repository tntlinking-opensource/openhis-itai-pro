/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.medication.medicationrequest;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter

/**
 * MedicationOrderGroupAdjustedEvent：health-domain-shared 跨领域共享模型。
 */
public class MedicationOrderGroupAdjustedEvent extends DomainEvent {
    /** ids（MedicationOrderGroupAdjusted 业务上下文）。 */
    private List<Long> ids;
    private Long groupId;

    /** 操作人主键（MedicationOrderGroupAdjustedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MedicationOrderGroupAdjustedEvent() {
        super();
    }

    /**
     * 处理Long id, List<Long> ids, Long groupId逻辑。
     * @return 返回结果
     */
    public MedicationOrderGroupAdjustedEvent(Long id, List<Long> ids, Long groupId, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_REQUEST);
        this.ids = ids;
        this.groupId = groupId;
        this.operatorId = operatorId;
    }
}
