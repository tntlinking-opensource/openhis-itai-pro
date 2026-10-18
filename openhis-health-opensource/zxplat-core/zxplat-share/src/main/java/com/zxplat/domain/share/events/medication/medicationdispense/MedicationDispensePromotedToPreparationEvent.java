/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.medication.medicationdispense;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 药品发放单由草稿推进为待配药事件（门诊收费等链路）。
 */
@Getter
@Setter
public class MedicationDispensePromotedToPreparationEvent extends DomainEvent {

    /** medReq主键（MedicationDispensePromotedToPreparationEvent）。 */
    private Long medReqId;

    /** 操作人主键（MedicationDispensePromotedToPreparationEvent）。 */
    private Long operatorId;

    /** 发放单持久化状态码（与写模型一致），供读模型投影使用。 */
    private String statusEnumCode;

    public MedicationDispensePromotedToPreparationEvent() {
        super();
    }

    public MedicationDispensePromotedToPreparationEvent(Long dispenseId, Long medReqId, Long operatorId, String statusEnumCode) {
        super(dispenseId, AggregateTypeConstants.MEDICATION_DISPENSE);
        this.medReqId = medReqId;
        this.operatorId = operatorId;
        this.statusEnumCode = statusEnumCode;
    }
}
