/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 就诊平台挂号登记快照（列表 enrichment / 待挂号撤销判定）。
 */
@Data
@Accessors(chain = true)
public class OutpatientRegistrationPlatformQueryExtensionOut {

    /** 就诊主键。 */
    private Long encounterId;

    /** 医保合同主键。 */
    private Long contractId;

    /** 医保就诊 ID。 */
    private String mdtrtId;

    /** 参保地医保区划。 */
    private String insuplcAdmdvs;

    /** 是否已在平台完成挂号登记。 */
    private Boolean platformRegistered;

    /** 是否已发生医保预结算（影响退号策略）。 */
    private Boolean insurancePreSettled;

    /** 医保基金支付总额（展示用）。 */
    private BigDecimal ybFundPaySumamt;

    /** 医保个人账户支付（展示用）。 */
    private BigDecimal ybAcctPay;

    /** 医保个人现金支付（展示用）。 */
    private BigDecimal ybPsnCashPay;
}
