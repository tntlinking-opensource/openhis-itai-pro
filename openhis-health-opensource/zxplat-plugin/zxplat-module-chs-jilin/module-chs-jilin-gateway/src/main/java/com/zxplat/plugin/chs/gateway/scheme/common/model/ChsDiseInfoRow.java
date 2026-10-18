/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.common.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** diseinfo 单行（2203/2401/2402/2403 共用） */
@Data
@Accessors(chain = true)
public class ChsDiseInfoRow {

    @JSONField(name = "mdtrt_id")
    private String mdtrtId;

    @JSONField(name = "psn_no")
    private String psnNo;

    @JSONField(name = "diag_type")
    private String diagType;

    @JSONField(name = "maindiag_flag")
    private String maindiagFlag;

    @JSONField(name = "diag_srt_no")
    private String diagSrtNo;

    @JSONField(name = "diag_code")
    private String diagCode;

    @JSONField(name = "diag_name")
    private String diagName;

    @JSONField(name = "diag_dept")
    private String diagDept;

    @JSONField(name = "dise_dor_no")
    private String diseDorNo;

    @JSONField(name = "dise_dor_name")
    private String diseDorName;

    @JSONField(name = "diag_time")
    private String diagTime;

    /**
     * 有效标志（平台 vali_flag；挂号/门诊上传有效诊断时填 {@code 1}）。
     */
    @JSONField(name = "vali_flag")
    private String valiFlag;
}
