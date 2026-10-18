/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.outpatient.model;

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

    /** 病种编码（慢特病等按 opsp_dise_cod 上送；普门可空）。 */
    @JSONField(name = "dise_codg")
    private String diseCodg;

    /** 病种名称（与 dise_codg 成对；普门可空）。 */
    @JSONField(name = "dise_name")
    private String diseName;
}
