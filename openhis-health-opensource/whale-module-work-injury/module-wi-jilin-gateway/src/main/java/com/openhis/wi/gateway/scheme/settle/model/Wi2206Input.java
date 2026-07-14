/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.settle.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 【2206】费用预结算 input（10-P0 §9.1）。
 */
@Data
@Accessors(chain = true)
public class Wi2206Input {

    /** 门诊/住院流水号 ipt_otp_no。 */
    @JSONField(name = "ipt_otp_no")
    private String iptOtpNo;

    /** 预结算传空；2207 传 2206 出参 mdtrt_id。 */
    @JSONField(name = "mdtrt_id")
    private String mdtrtId;

    /** 医疗类别 med_type。 */
    @JSONField(name = "med_type")
    private String medType;

    /** 结算时间 yyyyMMddHHmmss。 */
    @JSONField(name = "setl_time")
    private String setlTime;

    /** 出院时间 yyyyMMddHHmmss（门诊同 adm）。 */
    @JSONField(name = "dscg_time")
    private String dscgTime;

    /** 出院原因 dscg_trt_rslt；门诊默认 01。 */
    @JSONField(name = "dscg_trt_rslt")
    private String dscgTrtRslt;

    /** 诊断编码 diag_code。 */
    @JSONField(name = "diag_code")
    private String diagCode;

    /** 人员编号 psn_no。 */
    @JSONField(name = "psn_no")
    private String psnNo;

    /** 入院/开单科室编码 adm_dept_codg。 */
    @JSONField(name = "adm_dept_codg")
    private String admDeptCodg;

    /** 主治医生编码 atddr_no。 */
    @JSONField(name = "atddr_no")
    private String atddrNo;

    /** 诊断描述 diag_dscr。 */
    @JSONField(name = "diag_dscr")
    private String diagDscr;
}
