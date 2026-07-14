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
 * 住院出院召回领域事件（DM-ADM-002 §6.1.25，AS-IP-NUR-002 IF-IP-NUR-ATD-07）。
 *
 * <p>载荷含召回后目标状态；{@code requiresBedAssignment=true} 表示须护士二次分床（已清床轨）。</p>
 */
@Getter
@Setter
public class InpatientDischargeRecalledEvent extends DomainEvent {

    /** 患者主键。 */
    private Long patientId;

    /** 召回前就诊状态码（典型 {@code COMPLETED} 或 {@code DISCHARGED}）。 */
    private String previousStatusEnum;

    /** 召回后就诊状态码（{@code IN_PROGRESS} 或 {@code REGISTERED}）。 */
    private String statusEnum;

    /**
     * 是否须二次分床。
     *
     * <p>原态为 {@code DISCHARGED} 时为 {@code true}；原态为 {@code COMPLETED} 时为 {@code false}。</p>
     */
    private Boolean requiresBedAssignment;

    /** 已清床轨写入的活动病区位置主键；未清床轨为空。 */
    private Long wardLocationId;

    /** 无参构造器（反序列化用）。 */
    public InpatientDischargeRecalledEvent() {
        super();
    }

    /**
     * 构造出院召回事件。
     *
     * @param encounterId 就诊主键
     * @param patientId 患者主键
     */
    public InpatientDischargeRecalledEvent(Long encounterId, Long patientId) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.patientId = patientId;
    }
}
