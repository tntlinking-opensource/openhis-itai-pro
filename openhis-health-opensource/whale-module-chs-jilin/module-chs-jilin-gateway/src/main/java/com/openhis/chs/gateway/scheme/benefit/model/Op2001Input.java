/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.benefit.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.openhis.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【2001】人员待遇享受检查 input */
public class Op2001Input extends ChsDataInput<Op2001Input.Op2001Data> {

    /** 2001 input.data */
    @Data
    @Accessors(chain = true)
    public static class Op2001Data {

        @JSONField(name = "psn_no")
        private String psnNo;

        private String insutype;

        @JSONField(name = "fixmedins_code")
        private String fixmedinsCode;

        @JSONField(name = "med_type")
        private String medType;

        private String begntime;
    }
}
