/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.mrms.caserecord.resolver;

import java.math.BigDecimal;

/**
 * 病案首页 4401 {@code selfpay_amt} 可选解析（由 application 适配结算/医保读侧）。
 *
 * <p>口径：医保已结算 → Σ 个人负担（{@code psn_part_amt}）；纯自费 → 医疗费总额。</p>
 */
public interface HomepageSelfpayAmtResolver {

    /**
     * 按就诊解析自付金额。
     *
     * @param encounterId   就诊主键
     * @param medfeeSumamt  已汇总的医疗费总额（纯自费回落用）
     * @return 自付金额；无法解析时 null
     */
    BigDecimal resolveByEncounterId(Long encounterId, BigDecimal medfeeSumamt);
}
