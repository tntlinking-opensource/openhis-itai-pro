/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.reconciliation.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 本地清算申请流水行（出参）。 */
@Data
@Accessors(chain = true)
public class LocalClearApplyResponse {

    /** 本地记录 ID */
    private Long id;
    /** 契约业务编号 */
    private String contractBusNo;
    /** 清算类别 */
    private String clrType;
    /** 清算经办机构 */
    private String clrOptins;
    /** 开始日期 */
    private String begndate;
    /** 结束日期 */
    private String enddate;
    /** 医疗费总额 */
    private BigDecimal medfeeSumamt;
    /** 基金申报总额 */
    private BigDecimal fundAppySum;
    /** 个人账户支付 */
    private BigDecimal acctPay;
    /** 现金支付金额 */
    private BigDecimal cashPayamt;
    /** 清算申请事件 ID */
    private String clrAppyEvtId;
    /** 平台清算状态 */
    private String clrStas;
    /** 本地状态 */
    private String status;
    /** 创建时间 */
    private String createdAt;
}
