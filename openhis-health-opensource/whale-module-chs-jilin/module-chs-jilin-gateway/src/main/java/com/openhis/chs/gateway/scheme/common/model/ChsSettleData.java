/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.common.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 2206/2207/2303/2304 input.data 结算体 */
@Data
@Accessors(chain = true)
public class ChsSettleData {

    @JSONField(name = "psn_no")
    private String psnNo;

    @JSONField(name = "mdtrt_cert_type")
    private String mdtrtCertType;

    @JSONField(name = "mdtrt_cert_no")
    private String mdtrtCertNo;

    @JSONField(name = "med_type")
    private String medType;

    @JSONField(name = "medfee_sumamt")
    private BigDecimal medfeeSumamt;

    @JSONField(name = "psn_setlway")
    private String psnSetlway;

    @JSONField(name = "mdtrt_id")
    private String mdtrtId;

    @JSONField(name = "chrg_bchno")
    private String chrgBchno;

    @JSONField(name = "insutype")
    private String insutype;

    @JSONField(name = "acct_used_flag")
    private String acctUsedFlag;

    @JSONField(name = "insuplc_admdvs")
    private String insuplcAdmdvs;

    @JSONField(name = "mdtrtarea_admvs")
    private String mdtrtareaAdmvs;

    @JSONField(name = "mid_setl_flag")
    private String midSetlFlag;

    @JSONField(name = "dscgTime")
    private String dscgTime;

    @JSONField(name = "fulamt_ownpay_amt")
    private BigDecimal fulamtOwnpayAmt;

    @JSONField(name = "overlmt_selfpay")
    private BigDecimal overlmtSelfpay;

    @JSONField(name = "preselfpay_amt")
    private BigDecimal preselfpayAmt;

    @JSONField(name = "inscp_scp_amt")
    private BigDecimal inscpScpAmt;

    private String invono;

    @JSONField(name = "minpacunt_drug_trac_cnt")
    private Integer minpacuntDrugTracCnt;

    @JSONField(name = "mcs_trac_cnt")
    private Integer mcsTracCnt;
}
