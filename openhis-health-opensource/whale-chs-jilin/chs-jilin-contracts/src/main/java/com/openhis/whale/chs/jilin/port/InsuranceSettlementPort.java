/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port;

import com.openhis.whale.chs.jilin.port.model.PaymentSettlementTrigger;

/**
 * 收费↔医保挂接 Port：收银域结算完成后由 bridge/application-insurance 实现，驱动协同单/2207 等。
 *
 * <p>core {@code health-application} 不直接依赖本 Port；通过领域事件 + 本 Port 实现解耦。</p>
 */
public interface InsuranceSettlementPort {

    /**
     * 门诊/住院收银结算完成后回调；实现方按契约判断是否启动医保协同。
     */
    void onPaymentSettled(PaymentSettlementTrigger trigger);
}
