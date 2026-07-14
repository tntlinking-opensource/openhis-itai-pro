/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.outpatient.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【2201】门诊挂号 input.data */
@Data
@Accessors(chain = true)
public class Op2201RegData {

    @JSONField(name = "psn_no")
    private String psnNo;

    private String insutype;

    private String begntime;

    @JSONField(name = "mdtrt_cert_type")
    private String mdtrtCertType;

    @JSONField(name = "mdtrt_cert_no")
    private String mdtrtCertNo;

    @JSONField(name = "ipt_otp_no")
    private String iptOtpNo;

    @JSONField(name = "atddr_no")
    private String atddrNo;

    @JSONField(name = "dr_name")
    private String drName;

    @JSONField(name = "dept_code")
    private String deptCode;

    @JSONField(name = "dept_name")
    private String deptName;

    private String caty;
}
