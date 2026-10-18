/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.settlement.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 平台 {@code setlinfo} 与领域结算实体之间的金额/身份字段块。
 *
 * <p>由 gateway {@code ChsSetlinfoOutput} 映射而来，供撤销凭证落库使用；不依赖 gateway scheme 包。<。</p> */
@Data
@Accessors(chain = true)
public class PlatformSetlinfoFields {

    /** 平台结算 ID */
    private String setlId;
    /** 平台就诊 ID */
    private String mdtrtId;
    /** 人员编号 */
    private String psnNo;
    /** 医疗费总额 */
    private BigDecimal medfeeSumamt;
    /** 基金支付总额 */
    private BigDecimal fundPaySumamt;
    /** 个人账户支出 */
    private BigDecimal acctPay;
    /** 个人现金支出 */
    private BigDecimal psnCashPay;
    /** 全自费金额 */
    private BigDecimal fulamtOwnpayAmt;
    /** 超限价自费 */
    private BigDecimal overlmtSelfpay;
    /** 先行自付 */
    private BigDecimal preselfpayAmt;
    /** 符合政策范围金额 */
    private BigDecimal inscpScpAmt;
    /** 险种类型 */
    private String insutype;
    /** 医疗类别 */
    private String medType;
    /** 人员姓名 */
    private String psnName;
    /** 公务员标志 */
    private String cvlservFlag;
    /** 结算时间（平台原文字段） */
    private String setlTime;
    /** 统筹基金支出 */
    private BigDecimal hifpPay;
    /** 公务员医疗补助 */
    private BigDecimal cvlservPay;
    /** 居民大病保险 */
    private BigDecimal hifmiPay;
    /** 职工大额补助 */
    private BigDecimal hifobPay;
    /** 企业补充医疗 */
    private BigDecimal hifesPay;
    /** 基本统筹自付比例 */
    private BigDecimal poolPropSelfpay;
    /** 医疗救助 */
    private BigDecimal mafPay;
    /** 其他支出 */
    private BigDecimal othPay;
    /** 个人负担总金额 */
    private BigDecimal psnPartAmt;
    /** 医院负担 */
    private BigDecimal hospPartAmt;
    /** 余额 */
    private BigDecimal balc;
    /** 账户共济支付 */
    private BigDecimal acctMulaidPay;
    /** 人员类别 */
    private String psnType;
    /** 实际支付起付线 */
    private BigDecimal actPayDedc;
    /** 伤残人员医疗保障 */
    private BigDecimal hifdmPay;
    /** 扩展字段（平台 JSON） */
    private String expContent;
    /** 医药机构结算 ID */
    private String medinsSetlId;
    /** 清算经办机构 */
    private String clrOptins;
    /** 清算方式 */
    private String clrWay;
    /** 清算类别 */
    private String clrType;
}
