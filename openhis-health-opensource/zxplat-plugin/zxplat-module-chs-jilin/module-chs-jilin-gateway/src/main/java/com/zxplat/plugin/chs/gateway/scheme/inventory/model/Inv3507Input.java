/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.inventory.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.zxplat.plugin.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【3507】删除进销存数据 input */
public class Inv3507Input extends ChsDataInput<Inv3507Input.Inv3507Data> {

    /** 3507 input.data */
    @Data
    @Accessors(chain = true)
    public static class Inv3507Data {

        @JSONField(name = "fixmedins_bchno")
        private String fixmedinsBchno;

        @JSONField(name = "inv_data_type")
        private String invDataType;
    }
}
