/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.reconciliation.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.openhis.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【3205A】查询清算申请状态 input */
public class Recon3205AInput extends ChsDataInput<Recon3205AInput.Recon3205AData> {

    /** 3205A input.data */
    @Data
    @Accessors(chain = true)
    public static class Recon3205AData {

        @JSONField(name = "clr_optins")
        private String clrOptins;

        @JSONField(name = "clr_stas")
        private String clrStas;

        @JSONField(name = "clr_ym")
        private String clrYm;
    }
}
