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
 * 药品发放作废事件。
 */
@Getter
@Setter
public class MedicationDispenseDeclinedEvent extends DomainEvent {
    /** 未执行原因枚举编码。 */
    private String notPerformedReasonEnum;

    /** 操作人主键（MedicationDispenseDeclinedEvent）。 */
    private Long operatorId;

    /** 发放单持久化状态码（与写模型一致），供读模型投影使用。 */
    private String statusEnumCode;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MedicationDispenseDeclinedEvent() {
        super();
    }

    /**
     * 处理Long id逻辑。
     * @return 返回结果
     */
    public MedicationDispenseDeclinedEvent(Long id, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_DISPENSE);
        this.operatorId = operatorId;
    }

    /**
     * 处理Long id, Integer notPerformedReasonEnum逻辑。
     * @return 返回结果
     */
    public MedicationDispenseDeclinedEvent(Long id, String notPerformedReasonEnum) {
        this(id, notPerformedReasonEnum, null);
    }

    public MedicationDispenseDeclinedEvent(Long id, String notPerformedReasonEnum, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_DISPENSE);
        this.notPerformedReasonEnum = notPerformedReasonEnum;
        this.operatorId = operatorId;
    }
}
