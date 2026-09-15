/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.settle.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 【2206】费用预结算 output（10-P0 §9.2；2207 复用字段结构）。
 */
@Data
@Accessors(chain = true)
public class Wi2206Output {

    /** 门诊/住院流水号 ipt_otp_no。 */
    @JSONField(name = "ipt_otp_no")
    private String iptOtpNo;

    /** 平台就诊 ID；2207 input 必填。 */
    @JSONField(name = "mdtrt_id")
    private String mdtrtId;

    /** 医疗费总额 medfee_sumamt。 */
    @JSONField(name = "medfee_sumamt")
    private BigDecimal medfeeSumamt;

    /** 统筹支付 hifp_pay。 */
    @JSONField(name = "hifp_pay")
    private BigDecimal hifpPay;

    /** 个人现金 psn_cash_pay。 */
    @JSONField(name = "psn_cash_pay")
    private BigDecimal psnCashPay;

    /** 2207 出参 setl_id；2206 通常为空。 */
    @JSONField(name = "setl_id")
    private String setlId;

    /** 结算时间 yyyyMMddHHmmss（2207 出参；对账按 setl_time 归日）。 */
    @JSONField(name = "setl_time")
    private String setlTime;
}
