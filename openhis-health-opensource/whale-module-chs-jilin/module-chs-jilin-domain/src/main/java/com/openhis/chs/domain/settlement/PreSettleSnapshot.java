/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement;

import java.math.BigDecimal;

/**
 * 2206/2303 预结算报价只读视图（门诊/住院统一） */
public interface PreSettleSnapshot {

    Long getId();

    String getMdtrtId();

    String getPsnNo();

    String getStatus();

    BigDecimal getMedfeeSumamt();

    BigDecimal getFundPaySumamt();

    BigDecimal getAcctPay();

    BigDecimal getPsnCashPay();

    BigDecimal getFulamtOwnpayAmt();

    BigDecimal getOverlmtSelfpay();

    BigDecimal getPreselfpayAmt();

    BigDecimal getInscpScpAmt();

    /** 平台收费批次键：门诊为 chrg_bchno，住院为 {@code IP{preSettleId}} */
    String resolveBatchKey();
}
