/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.workflow.activitydefinition;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 诊疗项目定价已更新事件。
 *
 * <p>SDD-WKF-001 §7 最小载荷：activityDefinitionId（via super.aggregateId）、
 * oldStandardPrice、newStandardPrice、reasonCode。</p>
 */
@Getter
@Setter
public class ActivityDefinitionPriceUpdatedEvent extends DomainEvent {

    /** 变更前标准价。 */
    private BigDecimal oldStandardPrice;

    /** 变更后标准价。 */
    private BigDecimal newStandardPrice;

    /** 变更原因代码。 */
    private String reasonCode;

    /**
     * 默认构造函数（反序列化用）。
     */
    public ActivityDefinitionPriceUpdatedEvent() {
        super();
    }

    /**
     * 构造诊疗项目定价已更新事件。
     *
     * @param activityDefinitionId 诊疗项目主键（aggregateId）
     * @param oldStandardPrice     变更前标准价
     * @param newStandardPrice     变更后标准价
     * @param reasonCode           变更原因代码
     */
    public ActivityDefinitionPriceUpdatedEvent(Long activityDefinitionId, BigDecimal oldStandardPrice,
                                               BigDecimal newStandardPrice, String reasonCode) {
        super(activityDefinitionId, AggregateTypeConstants.ACTIVITY_DEFINITION);
        this.oldStandardPrice = oldStandardPrice;
        this.newStandardPrice = newStandardPrice;
        this.reasonCode = reasonCode;
    }
}
