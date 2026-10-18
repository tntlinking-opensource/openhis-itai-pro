/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.medicalinfo.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.zxplat.plugin.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【5202】诊断信息查询 input */
public class Med5202Input extends ChsDataInput<Med5202Input.Med5202Data> {

    /** 5202 input.data */
    @Data
    @Accessors(chain = true)
    public static class Med5202Data {

        @JSONField(name = "mdtrt_id")
        private String mdtrtId;

        @JSONField(name = "psn_no")
        private String psnNo;
    }
}
