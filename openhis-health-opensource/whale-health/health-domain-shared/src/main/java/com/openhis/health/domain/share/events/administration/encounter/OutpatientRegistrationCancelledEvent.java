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
 * 门诊就诊取消事件。
 *
 * <p>退号落库后发布；须携带取消后的就诊状态码与取消时刻，供门诊信息/挂号列表/挂号单等读模型一致物化。</p>
 */
@Getter
@Setter
public class OutpatientRegistrationCancelledEvent extends DomainEvent {

    /** 患者主键。 */
    private Long patientId;
    /** 取消原因说明。 */
    private String cancelReason;
    /**
     * 取消生效后的就诊状态枚举 code（与 {@code Encounter.statusEnum} 一致，通常为 {@code CANCELLED}）。
     */
    private String statusEnum;
    /** 退号业务时刻（用于读模型写入结束时刻等）。 */
    private LocalDateTime cancelledAt;

    /**
     * 框架反序列化用无参构造。
     */
    public OutpatientRegistrationCancelledEvent() {
        super();
    }

    /**
     * 构造门诊就诊取消事件（领域服务在发布前须补全 {@link #statusEnum} 与 {@link #cancelledAt}）。
     *
     * @param encounterId 就诊主键
     * @param patientId 患者主键
     * @param cancelReason 取消原因
     */
    public OutpatientRegistrationCancelledEvent(Long encounterId, Long patientId, String cancelReason) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.patientId = patientId;
        this.cancelReason = cancelReason;
    }
}
