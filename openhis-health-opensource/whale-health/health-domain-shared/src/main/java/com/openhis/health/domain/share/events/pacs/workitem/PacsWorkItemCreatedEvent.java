/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.pacs.workitem;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 检查工单已创建事件。
 */
@Getter
@Setter
public class PacsWorkItemCreatedEvent extends DomainEvent {

    /** 患者主键。 */
    private Long patientId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 登记/执行科室。 */
    private Long organizationId;

    /** 检查执行态枚举 code。 */
    private String statusEnum;

    /** 关联 ServiceRequest 主键列表。 */
    private List<Long> serviceRequestIds;

    /** 无参构造器（反序列化用）。 */
    public PacsWorkItemCreatedEvent() {
        super();
    }

    /**
     * @param workItemId 工单主键（聚合根 ID）
     * @param patientId 患者主键
     * @param encounterId 就诊主键
     * @param organizationId 科室主键
     * @param statusEnum 执行态
     * @param serviceRequestIds 关联医嘱列表
     */
    public PacsWorkItemCreatedEvent(
        Long workItemId,
        Long patientId,
        Long encounterId,
        Long organizationId,
        String statusEnum,
        List<Long> serviceRequestIds) {
        super(workItemId, AggregateTypeConstants.PACS_WORK_ITEM);
        this.patientId = patientId;
        this.encounterId = encounterId;
        this.organizationId = organizationId;
        this.statusEnum = statusEnum;
        this.serviceRequestIds = serviceRequestIds;
    }
}
