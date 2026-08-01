/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.settlement;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * 医保结算回执只读查询扩展（日结按 settlementRecordId 解析 insutype）。
 *
 * <p><b>吉林实现</b>：{@code InsuranceSettlementRecordLookupExtensionImpl} →
 * {@code YbSettlementRecordQueryService}。</p>
 */
public interface InsuranceSettlementRecordLookupExtension {

    /**
     * 扩展是否已装配。
     *
     * @return false 时日结险种维度回落为空码
     */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 批量解析结算回执险种类型。
     *
     * @param settlementRecordIds {@code yb_settlement_record.id} 集合
     * @return id → insutype；未命中不包含该 key
     */
    default Map<Long, String> mapInsutypeBySettlementRecordIds(Collection<Long> settlementRecordIds) {
        return Collections.emptyMap();
    }

    /**
     * 按支付单批量解析险种类型（桥表缺 {@code settlement_record_id} 时日结回落）。
     *
     * @param paymentIds {@code fin_payment_reconciliation.id} 集合
     * @return paymentId → insutype；未命中不包含该 key
     */
    default Map<Long, String> mapInsutypeByPaymentIds(Collection<Long> paymentIds) {
        return Collections.emptyMap();
    }
}
