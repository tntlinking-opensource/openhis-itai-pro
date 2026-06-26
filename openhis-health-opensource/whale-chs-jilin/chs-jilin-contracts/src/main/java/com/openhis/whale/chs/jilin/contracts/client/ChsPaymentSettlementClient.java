/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.client;

import com.openhis.whale.chs.jilin.contracts.dto.PaymentSettlementTrigger;

/**
 * 收费↔医保挂接 Client：收银域结算完成后由 adapter 实现，驱动协同单/2207 等。
 *
 * <p>core {@code health-application} 不直接依赖本 Client；通过领域事件解耦。</p>
 */
public interface ChsPaymentSettlementClient {

    /**
     * 门诊/住院收银结算完成后回调；实现方按契约判断是否启动医保协同。
     */
    void onPaymentSettled(PaymentSettlementTrigger trigger);
}
