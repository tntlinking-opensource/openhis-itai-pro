/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.audit.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** 3101 fsi_diagnose_dtos 元素 */
@Data
@Accessors(chain = true)
public class Audit3101DiagnoseRow {

    @JSONField(name = "dise_id")
    private String diseId;

    @JSONField(name = "inout_dise_type")
    private String inoutDiseType;

    @JSONField(name = "maindise_flag")
    private String maindiseFlag;

    @JSONField(name = "dias_srt_no")
    private String diasSrtNo;

    @JSONField(name = "dise_codg")
    private String diseCodg;

    @JSONField(name = "dise_name")
    private String diseName;

    @JSONField(name = "dise_date")
    private String diseDate;
}
