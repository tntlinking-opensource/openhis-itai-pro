/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.custom.chargediscountconfig;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 收费折扣配置已变更事件（新增/修改/启停/软删后发布；DM-CUS-016 §5）。
 *
 * <p>消费方可失效「是否启用打折」缓存；{@link #tenantId} 由事件基类/发布管道填充。</p>
 */
@Getter
@Setter
public class ChargeDiscountConfigChangedEvent extends DomainEvent {

    /**
     * 院区维度（可空；首版全院配置为空）。
     */
    private Long orgId;

    /**
     * 无参构造（反序列化等）。
     */
    public ChargeDiscountConfigChangedEvent() {
        super();
    }

    /**
     * 构造配置变更事件。
     *
     * @param configId 配置主键（聚合根 id）
     * @param orgId    院区，可空
     */
    public ChargeDiscountConfigChangedEvent(Long configId, Long orgId) {
        super(configId, AggregateTypeConstants.CHARGE_DISCOUNT_CONFIG);
        this.orgId = orgId;
    }
}
