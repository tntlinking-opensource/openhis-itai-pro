/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.inpatient.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** 2401 input.mdtrtinfo 入院登记信息（单行） */
@Data
@Accessors(chain = true)
public class Ip2401MdtrtInfo {

    @JSONField(name = "psn_no")
    private String psnNo;

    private String insutype;

    private String begntime;

    @JSONField(name = "mdtrt_cert_type")
    private String mdtrtCertType;

    @JSONField(name = "mdtrt_cert_no")
    private String mdtrtCertNo;

    @JSONField(name = "med_type")
    private String medType;

    @JSONField(name = "ipt_no")
    private String iptNo;

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

    @JSONField(name = "insuplc_admdvs")
    private String insuplcAdmdvs;

    @JSONField(name = "mdtrtarea_admvs")
    private String mdtrtareaAdmvs;
}
