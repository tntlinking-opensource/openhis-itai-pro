/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.encounter;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 取消住院登记事件（DM-ADM-002 §6.1.23）。
 *
 * <p>取消落库后发布；须携带取消后的 {@code statusEnum} 与 {@code cancelledTime}，
 * 供 {@code InpatientInfo} 读模型及患者摘要投影一致物化。</p>
 */
@Getter
@Setter
public class InpatientAdmissionCancelledEvent extends DomainEvent {

    /** 患者主键。 */
    private Long patientId;

    /** 取消原因说明。 */
    private String cancelReason;

    /**
     * 取消生效后的就诊状态枚举 code（与 {@code Encounter.statusEnum} 一致，通常为 {@code CANCELLED}）。
     */
    private String statusEnum;

    /** 取消登记业务时刻（读模型写入 {@code end_time}）。 */
    private LocalDateTime cancelledTime;

    /**
     * 框架反序列化用无参构造。
     */
    public InpatientAdmissionCancelledEvent() {
        super();
    }

    /**
     * 构造住院登记取消事件（领域服务在发布前须补全 {@link #statusEnum} 与 {@link #cancelledTime}）。
     *
     * @param encounterId 住院就诊主键
     * @param patientId 患者主键
     * @param cancelReason 取消原因
     */
    public InpatientAdmissionCancelledEvent(Long encounterId, Long patientId, String cancelReason) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.patientId = patientId;
        this.cancelReason = cancelReason;
    }
}
