/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.medicalinfo.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.zxplat.plugin.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【5205】门慢门特用药查询 input */
public class Med5205Input extends ChsDataInput<Med5205Input.Med5205Data> {

    /** 5205 input.data */
    @Data
    @Accessors(chain = true)
    public static class Med5205Data {

        @JSONField(name = "psn_no")
        private String psnNo;

        private String begntime;

        private String endtime;
    }
}
