/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.encounter;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 门诊取消接诊事件：医生同意退号，就诊回退待诊；供医生站队列与挂号列表读模型刷新。
 */
@Getter
@Setter
public class OutpatientReceptionCancelledEvent extends DomainEvent {

    /** 患者主键。 */
    private Long patientId;
    /** 取消接诊发生时刻。 */
    private LocalDateTime cancelledAt;
    /** 取消接诊后就诊状态枚举 code（典型 {@code REGISTERED}）。 */
    private String statusEnum;

    /**
     * 反序列化用无参构造。
     */
    public OutpatientReceptionCancelledEvent() {
        super();
    }

    /**
     * 常用构造：就诊、患者与取消时刻。
     *
     * @param encounterId 就诊标识
     * @param patientId   患者主键
     * @param cancelledAt 取消接诊时刻
     */
    public OutpatientReceptionCancelledEvent(Long encounterId, Long patientId, LocalDateTime cancelledAt) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.patientId = patientId;
        this.cancelledAt = cancelledAt;
    }
}
