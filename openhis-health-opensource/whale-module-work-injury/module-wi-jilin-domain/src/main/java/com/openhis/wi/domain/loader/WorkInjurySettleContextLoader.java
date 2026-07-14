/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.loader;

import com.openhis.wi.domain.settle.command.WiSettleContext;

import java.util.List;

/**
 * 从 HIS 加载工伤结算上下文（bridge {@link com.openhis.wi.bridge.context.ChsWorkInjurySettleContextLoader} 实现）。
 */
public interface WorkInjurySettleContextLoader {

    /**
     * 门诊/住院收费预结算上下文（含 chargeItemIds → 2204 明细）。
     *
     * @param encounterId    就诊 ID
     * @param contractId     费用性质 contract
     * @param paymentDraftId   收银草稿 ID（可空）
     * @param chargeItemIds  待结算收费项
     * @return 2201～2207 报文组装用上下文
     */
    WiSettleContext loadSettleContext(
        Long encounterId,
        Long contractId,
        Long paymentDraftId,
        List<Long> chargeItemIds);

    /**
     * 挂号/9102/2301 上下文（无收费明细）。
     *
     * @param encounterId 就诊 ID
     * @param contractId  费用性质 contract
     * @return 登记链用上下文
     */
    WiSettleContext loadRegisterContext(Long encounterId, Long contractId);
}
