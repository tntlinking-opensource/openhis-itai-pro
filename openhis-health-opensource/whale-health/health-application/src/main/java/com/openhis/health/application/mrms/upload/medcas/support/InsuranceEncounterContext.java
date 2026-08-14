/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.medcas.support;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 4401/4101A/4102 组装用医保就诊与结算上下文（由 {@link InsuranceEncounterLoader} 填充）。
 */
@Getter
@Setter
public class InsuranceEncounterContext {

    /** 医保就诊 ID（mdtrt_id）。 */
    private String mdtrtId;

    /** 医保人员编号（psn_no）。 */
    private String psnNo;

    /** 结算 ID（setl_id，4101A/4102 必填）。 */
    private String setlId;

    /** 就诊流水号（mdtrt_sn，部分场景由 fixmedins+iptNo 拼接）。 */
    private String mdtrtSn;

    /** 定点医药机构编号。 */
    private String fixmedinsCode;

    /** 定点医药机构名称。 */
    private String fixmedinsName;

    /** 参保地医保区划。 */
    private String insuplcAdmdvs;

    /** 结算开始日期（4101A setl_begn_date）。 */
    private LocalDate setlBegnDate;

    /** 结算结束日期（4101A setl_end_date）。 */
    private LocalDate setlEndDate;

    /** 票据代码（4101A bill_code）。 */
    private String billCode;

    /** 票据号码（4101A bill_no）。 */
    private String billNo;

    /** 业务流水号（4101A biz_sn）。 */
    private String bizSn;

    /** 关联支付单主键（票据定位）。 */
    private Long paymentId;

    /** 医保结算回执主键（CHS YbSettlementRecord）。 */
    private Long settlementRecordId;
}
