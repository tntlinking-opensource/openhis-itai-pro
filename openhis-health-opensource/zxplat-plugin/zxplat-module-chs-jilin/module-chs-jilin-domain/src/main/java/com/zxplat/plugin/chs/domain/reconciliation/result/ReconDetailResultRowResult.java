/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.reconciliation.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 【3202】明细对账结果文件行（9102 下载解析） */
@Data
@Accessors(chain = true)
public class ReconDetailResultRowResult {

    private String psnNo;
    private String mdtrtId;
    private String setlId;
    private String msgid;
    private String stmtRslt;
    private String refdSetlFlag;
    private String memo;
    private BigDecimal medfeeSumamt;
    private BigDecimal fundPaySumamt;
    private BigDecimal acctPay;
    /** 本地补齐：人员姓名（平台结果文件无此列） */
    private String psnName;
    /** 本地补齐：结算时间 yyyy-MM-dd HH:mm:ss */
    private String setlTime;
    /** 本地补齐：结算回执状态 CONFIRMED / REVOKED */
    private String localStatus;
    /** 本地是否命中结算回执（BC 对照左栏） */
    private Boolean localExists;
    /** 本地补齐：医疗费总额 */
    private BigDecimal localMedfeeSumamt;
    /** 本地补齐：基金支付总额 */
    private BigDecimal localFundPaySumamt;
    /** 本地补齐：个人账户支出 */
    private BigDecimal localAcctPay;
}
