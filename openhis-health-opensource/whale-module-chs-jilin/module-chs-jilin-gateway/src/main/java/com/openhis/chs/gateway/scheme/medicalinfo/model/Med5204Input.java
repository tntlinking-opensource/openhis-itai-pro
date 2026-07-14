/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.medicalinfo.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.openhis.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【5204】费用明细查询 input */
public class Med5204Input extends ChsDataInput<Med5204Input.Med5204Data> {

    /** 5204 input.data */
    @Data
    @Accessors(chain = true)
    public static class Med5204Data {

        @JSONField(name = "psn_no")
        private String psnNo;

        @JSONField(name = "mdtrt_id")
        private String mdtrtId;

        @JSONField(name = "setl_id")
        private String setlId;
    }
}
