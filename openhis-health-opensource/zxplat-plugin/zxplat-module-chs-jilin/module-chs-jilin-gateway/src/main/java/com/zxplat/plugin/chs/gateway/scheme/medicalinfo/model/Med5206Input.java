/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.medicalinfo.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.zxplat.plugin.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【5206】人员累计查询 input */
public class Med5206Input extends ChsDataInput<Med5206Input.Med5206Data> {

    /** 5206 input.data */
    @Data
    @Accessors(chain = true)
    public static class Med5206Data {

        @JSONField(name = "psn_no")
        private String psnNo;

        @JSONField(name = "cum_ym")
        private String cumYm;
    }
}
