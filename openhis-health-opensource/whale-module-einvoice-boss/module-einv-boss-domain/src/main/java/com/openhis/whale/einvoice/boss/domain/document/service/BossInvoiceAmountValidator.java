/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.document.service;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.einvoice.boss.domain.share.EinvBossErrorCodes;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 博思出站前金额硬校验（纯领域服务，无 Spring 依赖）。
 *
 * <p>不变量：明细合计必须等于开票总额，scale=2，容差 0；否则禁止调用博思。</p>
 */
@Service
public class BossInvoiceAmountValidator {

    /**
     * 校验明细合计与开票总额是否一致。
     *
     * @param totalAmt 开票总额
     * @param lineAmounts 各行实际金额
     * @throws BusinessException 差额不为零时抛出 {@link EinvBossErrorCodes#EINV_AMOUNT_MISMATCH}
     */
    public void validate(BigDecimal totalAmt, List<BigDecimal> lineAmounts) {
        // 前置：空明细视为合计 0，仍须与 totalAmt 对齐
        BigDecimal sum = BigDecimal.ZERO;
        if (lineAmounts != null) {
            for (BigDecimal lineAmount : lineAmounts) {
                if (lineAmount != null) {
                    sum = sum.add(lineAmount);
                }
            }
        }
        BigDecimal normalizedSum = sum.setScale(2, RoundingMode.HALF_UP);
        BigDecimal normalizedTotal = totalAmt.setScale(2, RoundingMode.HALF_UP);
        // 分支：差额非零则阻断出站，避免博思侧拒收或产生错票
        if (normalizedSum.compareTo(normalizedTotal) != 0) {
            throw new BusinessException(
                    EinvBossErrorCodes.EINV_AMOUNT_MISMATCH,
                    "明细合计 " + normalizedSum + " 与开票总额 " + normalizedTotal + " 不一致");
        }
    }
}
