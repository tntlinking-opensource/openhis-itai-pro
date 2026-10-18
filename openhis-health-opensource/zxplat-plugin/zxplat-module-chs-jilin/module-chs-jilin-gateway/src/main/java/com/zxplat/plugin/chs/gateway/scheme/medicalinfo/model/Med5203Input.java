/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.medicalinfo.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.zxplat.plugin.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【5203】结算信息查询 input */
public class Med5203Input extends ChsDataInput<Med5203Input.Med5203Data> {

    /** 5203 input.data */
    @Data
    @Accessors(chain = true)
    public static class Med5203Data {

        @JSONField(name = "psn_no")
        private String psnNo;

        @JSONField(name = "setl_id")
        private String setlId;

        @JSONField(name = "mdtrt_id")
        private String mdtrtId;
    }
}
