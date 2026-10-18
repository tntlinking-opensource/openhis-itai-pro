/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.charge.response;

import com.zxplat.application.outpatient.financial.charge.dto.PendingChargeItemRowDto;
import com.zxplat.application.outpatient.financial.charge.dto.ChargeSettlementInsuranceSummaryDto;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 加载待收费明细响应。
 */
@Data
@Accessors(chain = true)
public class LoadPendingChargeItemsResponse {
    /**
     * 就诊主键。
     */
    private Long encounterId;
    /**
     * 待收费项目明细列表。
     */
    private List<PendingChargeItemRowDto> items;
    /**
     * 是否允许就诊维度转医保/转自费（须同时存在默认自费与医保账户，纯自费挂号为 false）。
     */
    private Boolean accountSwitchEnabled;

    /**
     * 已结算支付的医保分摊摘要（与收费小票结算单口径一致；无成功支付时为空）。
     * <p>多笔成功支付时对应 {@link #settlementInsuranceOptions} 中当前选中项。</p>
     */
    private ChargeSettlementInsuranceSummaryDto settlementInsuranceSummary;

    /**
     * 本就诊全部成功支付的结算摘要列表（账单时间倒序）；供收费页切换支付单据。
     */
    private List<ChargeSettlementInsuranceSummaryDto> settlementInsuranceOptions;
}
