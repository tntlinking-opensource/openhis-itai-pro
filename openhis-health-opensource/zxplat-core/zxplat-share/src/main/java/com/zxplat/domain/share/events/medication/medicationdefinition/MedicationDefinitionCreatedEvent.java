/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.medication.medicationdefinition;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 药品定义创建事件。
 */
@Getter
@Setter
public class MedicationDefinitionCreatedEvent extends DomainEvent {
    /** 药品业务编码。 */
    private String busNo;
    /** 药品名称。 */
    private String name;
    /** 机构标识。 */
    private Long orgId;

    /** 操作人主键（MedicationDefinitionCreatedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MedicationDefinitionCreatedEvent() {
        super();
    }

    /**
     * 处理Long id, String busNo, String name, Long orgId逻辑。
     * @return 返回结果
     */
    public MedicationDefinitionCreatedEvent(Long id, String busNo, String name, Long orgId, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_DEFINITION);
        this.busNo = busNo;
        this.name = name;
        this.orgId = orgId;
        this.operatorId = operatorId;
    }
}
