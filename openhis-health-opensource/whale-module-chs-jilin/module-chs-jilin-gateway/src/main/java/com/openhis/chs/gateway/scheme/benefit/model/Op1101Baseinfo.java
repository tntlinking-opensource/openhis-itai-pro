/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.benefit.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【1101】人员信息 output.baseinfo */
@Data
@Accessors(chain = true)
public class Op1101Baseinfo {

    @JSONField(name = "psn_no")
    private String psnNo;

    @JSONField(name = "psn_name")
    private String psnName;

    private String certno;

    private String gend;

    private String brdy;
}
