/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.medication.medicationrequest;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 长期药品医嘱已取消停嘱领域事件（IF-IP-DOR-25）。
 */
@Getter
@Setter
public class LongTermMedicationOrderStopCancelledEvent extends DomainEvent {

    /** 操作人标识。 */
    private Long operatorId;

    /** 取消停嘱后请求状态（{@link com.zxplat.domain.share.enums.RequestStatus#getCode()}）。 */
    private String statusEnum;

    /** 无参构造，供序列化框架使用。 */
    public LongTermMedicationOrderStopCancelledEvent() {
        super();
    }

    /**
     * 构造取消停嘱事件载荷。
     *
     * @param id         药品申请主键
     * @param operatorId 操作人
     * @param statusEnum 恢复后的状态 code
     */
    public LongTermMedicationOrderStopCancelledEvent(Long id, Long operatorId, String statusEnum) {
        super(id, AggregateTypeConstants.MEDICATION_REQUEST);
        this.operatorId = operatorId;
        this.statusEnum = statusEnum;
    }
}
