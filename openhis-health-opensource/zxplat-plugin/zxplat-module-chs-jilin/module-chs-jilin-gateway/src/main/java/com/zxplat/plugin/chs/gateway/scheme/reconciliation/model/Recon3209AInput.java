/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.reconciliation.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.zxplat.plugin.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【3209A】跨省三方对账失败记录查询 input */
public class Recon3209AInput extends ChsDataInput<Recon3209AInput.Recon3209AData> {

    /** 3209A input.data */
    @Data
    @Accessors(chain = true)
    public static class Recon3209AData {

        @JSONField(name = "clr_type")
        private String clrType;

        private String begndate;

        private String enddate;

        @JSONField(name = "clr_optins")
        private String clrOptins;
    }
}
