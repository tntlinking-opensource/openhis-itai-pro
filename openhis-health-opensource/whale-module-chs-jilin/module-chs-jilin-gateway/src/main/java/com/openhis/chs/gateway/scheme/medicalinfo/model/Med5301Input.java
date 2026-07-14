/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.medicalinfo.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.openhis.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【5301】慢特病备案查询 input */
public class Med5301Input extends ChsDataInput<Med5301Input.Med5301Data> {

    /** 5301 input.data */
    @Data
    @Accessors(chain = true)
    public static class Med5301Data {

        @JSONField(name = "psn_no")
        private String psnNo;
    }
}
