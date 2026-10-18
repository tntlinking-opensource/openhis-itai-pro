/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.port;

import java.util.List;

import com.zxplat.domain.infrastructure.integration.payment.dto.ClientRefundResultDto;

/**
 * PEIS → HIS 门诊结算/退费端口（取消支付草稿、按原支付执行退费）。
 */
public interface HisOutpatientSettlementPort {

    /**
     * 取消就诊下全部支付草稿（可保留指定草稿主键）。
     *
     * @param encounterId          就诊主键
     * @param retainDraftPaymentId 保留的草稿支付主键；可为 null
     */
    void cancelAllPaymentDraftsForEncounter(Long encounterId, Long retainDraftPaymentId);

    /**
     * 按原支付主单执行账单退费（对齐门诊 IF-REF-05）。
     *
     * @param encounterId             就诊主键
     * @param originalPaymentId       原支付主键
     * @param refundingChargeItemIds  拟退收费行
     * @param billDate                账单日文本；可空
     * @param clientRefundResult      银联等 Win 回传；可空
     * @return 逆向支付单主键
     */
    Long executeBillRefundByPayment(
            Long encounterId,
            Long originalPaymentId,
            List<Long> refundingChargeItemIds,
            String billDate,
            ClientRefundResultDto clientRefundResult);
}
