/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.common.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 2206/2207/2303/2304/2305 output.setlinfo（仅含当前读写字段） */
@Data
@Accessors(chain = true)
public class ChsSetlinfoOutput {

    @JSONField(name = "setl_id")
    private String setlId;

    @JSONField(name = "mdtrt_id")
    private String mdtrtId;

    @JSONField(name = "psn_no")
    private String psnNo;

    @JSONField(name = "medfee_sumamt")
    private BigDecimal medfeeSumamt;

    @JSONField(name = "fund_pay_sumamt")
    private BigDecimal fundPaySumamt;

    @JSONField(name = "acct_pay")
    private BigDecimal acctPay;

    @JSONField(name = "psn_cash_pay")
    private BigDecimal psnCashPay;

    @JSONField(name = "fulamt_ownpay_amt")
    private BigDecimal fulamtOwnpayAmt;

    @JSONField(name = "overlmt_selfpay")
    private BigDecimal overlmtSelfpay;

    @JSONField(name = "preselfpay_amt")
    private BigDecimal preselfpayAmt;

    @JSONField(name = "inscp_scp_amt")
    private BigDecimal inscpScpAmt;

    private String insutype;

    @JSONField(name = "med_type")
    private String medType;

    @JSONField(name = "psn_name")
    private String psnName;

    @JSONField(name = "cvlserv_flag")
    private String cvlservFlag;

    @JSONField(name = "setl_time")
    private String setlTime;

    @JSONField(name = "hifp_pay")
    private BigDecimal hifpPay;

    @JSONField(name = "cvlserv_pay")
    private BigDecimal cvlservPay;

    @JSONField(name = "hifmi_pay")
    private BigDecimal hifmiPay;

    @JSONField(name = "hifob_pay")
    private BigDecimal hifobPay;

    @JSONField(name = "hifes_pay")
    private BigDecimal hifesPay;

    @JSONField(name = "pool_prop_selfpay")
    private BigDecimal poolPropSelfpay;

    @JSONField(name = "maf_pay")
    private BigDecimal mafPay;

    @JSONField(name = "oth_pay")
    private BigDecimal othPay;

    @JSONField(name = "psn_part_amt")
    private BigDecimal psnPartAmt;

    @JSONField(name = "hosp_part_amt")
    private BigDecimal hospPartAmt;

    private BigDecimal balc;

    @JSONField(name = "acct_mulaid_pay")
    private BigDecimal acctMulaidPay;

    @JSONField(name = "psn_type")
    private String psnType;

    @JSONField(name = "act_pay_dedc")
    private BigDecimal actPayDedc;

    @JSONField(name = "hifdm_pay")
    private BigDecimal hifdmPay;

    @JSONField(name = "exp_content")
    private String expContent;

    @JSONField(name = "medins_setl_id")
    private String medinsSetlId;

    @JSONField(name = "clr_optins")
    private String clrOptins;

    @JSONField(name = "clr_way")
    private String clrWay;

    @JSONField(name = "clr_type")
    private String clrType;
}
