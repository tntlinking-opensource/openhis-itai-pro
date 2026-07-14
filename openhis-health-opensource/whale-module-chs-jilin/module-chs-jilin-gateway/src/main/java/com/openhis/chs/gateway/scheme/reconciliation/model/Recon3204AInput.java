/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.reconciliation.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.openhis.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【3204A】撤销吉林清算申请 input */
public class Recon3204AInput extends ChsDataInput<Recon3204AInput.Recon3204AData> {

    /** 3204A input.data */
    @Data
    @Accessors(chain = true)
    public static class Recon3204AData {

        @JSONField(name = "clr_appy_evt_id")
        private String clrAppyEvtId;

        @JSONField(name = "clr_optins")
        private String clrOptins;
    }
}
