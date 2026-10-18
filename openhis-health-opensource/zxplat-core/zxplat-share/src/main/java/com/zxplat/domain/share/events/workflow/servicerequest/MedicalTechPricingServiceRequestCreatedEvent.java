/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.workflow.servicerequest;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 医技划价服务申请已创建事件。
 *
 * <p>与 {@link NursePricingServiceRequestCreatedEvent} 同构；来源为
 * {@code medical-tech-pricing}（AS-MT-CHG-001 诊疗轨）。</p>
 */
@Getter
@Setter
public class MedicalTechPricingServiceRequestCreatedEvent extends DomainEvent {

    /** 关联就诊标识。 */
    private Long encounterId;

    /** 患者标识。 */
    private Long patientId;

    /** 诊疗活动定义标识。 */
    private Long activityId;

    /** 请求数量。 */
    private BigDecimal quantity;

    /** 请求单位 code。 */
    private String unitCode;

    /** 持久化状态 code（save 后 VERIFIED）。 */
    private String statusEnum;

    /**
     * 默认构造函数（反序列化用）。
     */
    public MedicalTechPricingServiceRequestCreatedEvent() {
        super();
    }

    /**
     * 构造医技划价服务申请已创建事件。
     *
     * @param serviceRequestId 服务申请主键（aggregateId）
     * @param encounterId      关联就诊标识
     * @param patientId        患者标识
     * @param activityId       诊疗活动定义标识
     * @param quantity         请求数量
     * @param unitCode         请求单位 code
     * @param statusEnum       持久化请求状态 code
     */
    public MedicalTechPricingServiceRequestCreatedEvent(
        Long serviceRequestId,
        Long encounterId,
        Long patientId,
        Long activityId,
        BigDecimal quantity,
        String unitCode,
        String statusEnum) {
        super(serviceRequestId, AggregateTypeConstants.SERVICE_REQUEST);
        this.encounterId = encounterId;
        this.patientId = patientId;
        this.activityId = activityId;
        this.quantity = quantity;
        this.unitCode = unitCode;
        this.statusEnum = statusEnum;
    }
}
