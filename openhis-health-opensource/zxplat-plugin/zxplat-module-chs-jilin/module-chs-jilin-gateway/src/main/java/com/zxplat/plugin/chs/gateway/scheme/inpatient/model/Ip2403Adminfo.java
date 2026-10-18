/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.inpatient.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** 2403 input.adminfo 住院信息变更（单行） */
@Data
@Accessors(chain = true)
public class Ip2403Adminfo {

    @JSONField(name = "mdtrt_id")
    private String mdtrtId;

    @JSONField(name = "psn_no")
    private String psnNo;

    private String begntime;

    @JSONField(name = "mdtrt_cert_type")
    private String mdtrtCertType;

    @JSONField(name = "med_type")
    private String medType;

    @JSONField(name = "ipt_otp_no")
    private String iptOtpNo;

    @JSONField(name = "atddr_no")
    private String atddrNo;

    @JSONField(name = "chfpdr_name")
    private String chfpdrName;

    @JSONField(name = "adm_diag_dscr")
    private String admDiagDscr;

    @JSONField(name = "adm_dept_codg")
    private String admDeptCodg;

    @JSONField(name = "adm_dept_name")
    private String admDeptName;

    @JSONField(name = "adm_bed")
    private String admBed;

    @JSONField(name = "dscg_maindiag_code")
    private String dscgMaindiagCode;

    @JSONField(name = "dscg_maindiag_name")
    private String dscgMaindiagName;
}
