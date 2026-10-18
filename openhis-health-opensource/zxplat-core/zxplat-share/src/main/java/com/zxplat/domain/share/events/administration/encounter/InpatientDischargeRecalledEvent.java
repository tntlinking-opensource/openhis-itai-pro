/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.encounter;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 住院出院召回领域事件（DM-ADM-002 §6.1.25，AS-IP-FIN-004 召回写）。
 *
 * <p>载荷含召回后目标状态；已清床轨召回后 {@code requiresBedAssignment} 恒为 {@code false}
 * （不占床、不二次分床）。</p>
 */
@Getter
@Setter
public class InpatientDischargeRecalledEvent extends DomainEvent {

    /** 患者主键。 */
    private Long patientId;

    /** 召回前就诊状态码（典型 {@code DISCHARGED}）。 */
    private String previousStatusEnum;

    /** 召回后就诊状态码（{@code IN_PROGRESS}）。 */
    private String statusEnum;

    /**
     * 是否须二次分床。
     *
     * <p>已清床召回未占床轨恒为 {@code false}。</p>
     */
    private Boolean requiresBedAssignment;

    /** 召回时 INSERT 的活动病区位置主键。 */
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
