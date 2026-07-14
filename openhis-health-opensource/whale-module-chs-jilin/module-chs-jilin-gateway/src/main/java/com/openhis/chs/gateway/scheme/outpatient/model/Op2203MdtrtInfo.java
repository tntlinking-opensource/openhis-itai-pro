/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.outpatient.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【2203】就诊信息上传 mdtrtinfo */
@Data
@Accessors(chain = true)
public class Op2203MdtrtInfo {

    @JSONField(name = "mdtrt_id")
    private String mdtrtId;

    @JSONField(name = "psn_no")
    private String psnNo;

    @JSONField(name = "med_type")
    private String medType;

    private String begntime;

    @JSONField(name = "main_cond_dscr")
    private String mainCondDscr;
}
