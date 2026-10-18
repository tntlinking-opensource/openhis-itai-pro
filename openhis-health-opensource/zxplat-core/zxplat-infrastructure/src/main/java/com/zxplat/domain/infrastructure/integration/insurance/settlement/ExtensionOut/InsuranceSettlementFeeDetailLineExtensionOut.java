/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.settlement.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 医保费用明细报表 SPI 明细行出参（结算回执净行字段；busNo 由应用层 Encounter enrich）。
 */
@Data
@Accessors(chain = true)
public class InsuranceSettlementFeeDetailLineExtensionOut {

    /** 结算回执主键。 */
    private Long settlementRecordId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 平台人员姓名（psn_name）；可空。 */
    private String patientName;

    /** 平台结算 ID（setl_id）。 */
    private String setlId;

    /** 就医登记号（mdtrt_id）。 */
    private String mdtrtId;

    /** 结算时间；空 setl_time 时 SPI 可回落 created_at 供展示。 */
    private LocalDateTime setlTime;

    /** 医疗费总额。 */
    private BigDecimal medfeeSumamt;

    /** 基金支付总额。 */
    private BigDecimal fundPaySumamt;

    /** 个人账户支付。 */
    private BigDecimal acctPay;

    /** 现金支付。 */
    private BigDecimal psnCashPay;
}
