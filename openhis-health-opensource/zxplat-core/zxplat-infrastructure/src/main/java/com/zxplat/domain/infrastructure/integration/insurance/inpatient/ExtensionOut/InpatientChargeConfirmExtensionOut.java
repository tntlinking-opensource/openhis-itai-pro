/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 2304 住院正式结算确认结果。 */
@Data
@Accessors(chain = true)
public class InpatientChargeConfirmExtensionOut {

    /** 平台 setl_id。 */
    private String settlementId;
    /** 批次键 IP{preSettleId}。 */
    private String chargeBatchNo;
    /** 平台 mdtrt_id。 */
    private String encounterPlatformId;
    /** 本地结算回执 id。 */
    private Long settlementRecordId;
    /** 医疗费总额。 */
    private BigDecimal medfeeSumamt;
    /** 基金支付总额。 */
    private BigDecimal fundPaySumamt;
    /** 个人账户支出。 */
    private BigDecimal acctPay;
    /** 个人现金支出。 */
    private BigDecimal psnCashPay;
}
