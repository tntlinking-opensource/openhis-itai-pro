/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.workflow.servicerequest;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 住院滚费诊疗服务申请已创建事件（AS-IP-NUR-013 IF-IP-ARG-08）。
 *
 * <p>与 {@link NursePricingServiceRequestCreatedEvent} 对称，但来源为 {@code auto-roll-fees}，不计入护士站待校对统计。</p>
 */
@Getter
@Setter
public class AutoRollServiceRequestCreatedEvent extends DomainEvent {

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

    /** 诊疗类目持久化码。 */
    private String categoryEnum;

    /** 持久化状态 code（save 后通常为 VERIFIED）。 */
    private String statusEnum;

    /** 无参构造器（反序列化用）。 */
    public AutoRollServiceRequestCreatedEvent() {
        super();
    }

    /**
     * 构造滚费诊疗服务申请已创建事件。
     *
     * @param serviceRequestId 服务申请主键
     * @param encounterId      就诊标识
     * @param patientId        患者标识
     * @param activityId       活动定义标识
     * @param quantity         数量
     * @param unitCode         单位 code
     * @param categoryEnum     诊疗类目 code
     * @param statusEnum       持久化状态 code
     */
    public AutoRollServiceRequestCreatedEvent(
        Long serviceRequestId,
        Long encounterId,
        Long patientId,
        Long activityId,
        BigDecimal quantity,
        String unitCode,
        String categoryEnum,
        String statusEnum) {
        super(serviceRequestId, AggregateTypeConstants.SERVICE_REQUEST);
        this.encounterId = encounterId;
        this.patientId = patientId;
        this.activityId = activityId;
        this.quantity = quantity;
        this.unitCode = unitCode;
        this.categoryEnum = categoryEnum;
        this.statusEnum = statusEnum;
    }
}
