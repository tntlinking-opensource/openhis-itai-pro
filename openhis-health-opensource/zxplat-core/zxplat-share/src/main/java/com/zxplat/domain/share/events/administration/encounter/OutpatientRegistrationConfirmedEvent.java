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
 * 门诊挂号缴费确认领域事件。
 *
 * <p>就诊聚合在写侧完成「待挂号 → 已挂号」状态迁移并成功持久化后发布；与支付域
 * {@link com.zxplat.domain.share.events.financial.paymentreconciliation.OutpatientPaymentSettledEvent}
 * 解耦，避免普通门诊收费结算误推进就诊展示状态。读模型投影须从本事件载荷读取 {@link #statusEnum}，
 * 展示文案经 {@link com.zxplat.domain.share.utils.EnumUtil} 解析枚举后取得，禁止在投影器内编造状态码。</p>
 */
@Getter
@Setter
public class OutpatientRegistrationConfirmedEvent extends DomainEvent {

    /** 患者主键，供下游按患者维度关联或校验。 */
    private Long patientId;

    /**
     * 确认生效后的就诊状态持久化码（与 {@code adm_encounter.status_enum} 及 {@link com.zxplat.domain.share.enums.EncounterStatus#getCode()} 一致）。
     */
    private String statusEnum;

    /**
     * 框架反序列化用无参构造。
     */
    public OutpatientRegistrationConfirmedEvent() {
        super();
    }

    /**
     * 构造挂号确认事件（发布前须补全 {@link #statusEnum}）。
     *
     * @param encounterId 就诊聚合主键，作为领域事件 aggregateId
     * @param patientId     患者主键
     */
    public OutpatientRegistrationConfirmedEvent(Long encounterId, Long patientId) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.patientId = patientId;
    }
}
