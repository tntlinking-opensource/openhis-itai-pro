/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.benefit.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【1101】人员信息 output.idetinfo 行 */
@Data
@Accessors(chain = true)
public class Op1101IdetInfoRow {

    @JSONField(name = "psn_idet_type")
    private String psnIdetType;

    @JSONField(name = "psn_type_lv")
    private String psnTypeLv;

    private String memo;

    private String begntime;

    private String endtime;
}
