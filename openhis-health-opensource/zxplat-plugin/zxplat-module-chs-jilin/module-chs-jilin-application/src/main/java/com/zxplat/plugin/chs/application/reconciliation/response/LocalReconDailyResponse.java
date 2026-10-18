/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.reconciliation.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 本地日对账流水行（出参）。 */
@Data
@Accessors(chain = true)
public class LocalReconDailyResponse {

    /** 本地记录 ID */
    private Long id;
    /** 契约业务编号 */
    private String contractBusNo;
    /** 险种类型 */
    private String insutype;
    /** 清算类别 */
    private String clrType;
    /** 结算经办机构 */
    private String setlOptins;
    /** 对账开始日期 */
    private String stmtBegndate;
    /** 对账结束日期 */
    private String stmtEnddate;
    /** 医疗费总额 */
    private BigDecimal medfeeSumamt;
    /** 基金支付总额 */
    private BigDecimal fundPaySumamt;
    /** 个人账户支付 */
    private BigDecimal acctPay;
    /** 定点医药机构结算笔数 */
    private Integer fixmedinsSetlCnt;
    /** 对账结果码 */
    private String stmtRslt;
    /** 对账结果说明 */
    private String stmtRsltDscr;
    /** 创建时间 */
    private String createdAt;
}
