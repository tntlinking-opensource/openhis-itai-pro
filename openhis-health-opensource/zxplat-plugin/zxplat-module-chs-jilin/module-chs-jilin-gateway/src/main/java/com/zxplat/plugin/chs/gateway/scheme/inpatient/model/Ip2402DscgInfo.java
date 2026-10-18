/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.inpatient.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** 2402 input.dscginfo 出院登记信息（单行） */
@Data
@Accessors(chain = true)
public class Ip2402DscgInfo {

    @JSONField(name = "mdtrt_id")
    private String mdtrtId;

    @JSONField(name = "psn_no")
    private String psnNo;

    private String insutype;

    private String endtime;

    @JSONField(name = "dscg_dept_codg")
    private String dscgDeptCodg;

    @JSONField(name = "dscg_dept_name")
    private String dscgDeptName;

    @JSONField(name = "dscg_bed")
    private String dscgBed;

    @JSONField(name = "dscg_way")
    private String dscgWay;
}
