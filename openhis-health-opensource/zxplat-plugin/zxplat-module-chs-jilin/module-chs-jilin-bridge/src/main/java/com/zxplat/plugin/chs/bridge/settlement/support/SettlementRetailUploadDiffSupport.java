/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.settlement.support;

import com.zxplat.domain.share.utils.MonetaryAmountSupport;

import java.math.BigDecimal;

/**
 * 院内零售合计与医保费用上传合计分差（结算快照 / 日结勾稽）。
 *
 * <p>分差 = 结算两位刻度后的 {@code retail − upload}；不入 3201 对账口径。
 * 2204/2301 折算单价按 6 位平摊后，分差通常为 0；本支持仍保留供历史/极端舍入勾稽。</p>
 */
public final class SettlementRetailUploadDiffSupport {

    private SettlementRetailUploadDiffSupport() {
    }

    /**
     * 按零售/上传合计计算分差三件套（两位 HALF_UP）。
     *
     * @param retailSum 院内收费合计（可为 6 位行加总）
     * @param uploadSum 2204/2301 明细合计
     * @return 三位金额；入参可空按 0
     */
    public static RetailUploadDiffAmounts of(BigDecimal retailSum, BigDecimal uploadSum) {
        BigDecimal retail = MonetaryAmountSupport.scaleSettle(
            MonetaryAmountSupport.nullToZero(retailSum));
        BigDecimal upload = MonetaryAmountSupport.scaleSettle(
            MonetaryAmountSupport.nullToZero(uploadSum));
        BigDecimal diff = MonetaryAmountSupport.scaleSettle(retail.subtract(upload));
        return new RetailUploadDiffAmounts(retail, upload, diff);
    }

    /**
     * 零售/上传/分差三件套。
     *
     * @param retailMedfeeSumamt   院内收费合计（2 位）
     * @param uploadMedfeeSumamt   上传明细合计（2 位）
     * @param retailUploadDiffAmt  分差 retail−upload（2 位）
     */
    public record RetailUploadDiffAmounts(
        BigDecimal retailMedfeeSumamt,
        BigDecimal uploadMedfeeSumamt,
        BigDecimal retailUploadDiffAmt) {
    }
}
