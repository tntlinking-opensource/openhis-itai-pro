/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.catalog.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.openhis.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【㌳〲】目录对照撤销 input 壳 */
public class Cat3302Input extends ChsDataInput<Cat3302Input.Cat3302Data> {

    /** 3302 input.data 节点 */
    @Data
    @Accessors(chain = true)
    public static class Cat3302Data {

        @JSONField(name = "fixmedins_code")
        private String fixmedinsCode;

        @JSONField(name = "fixmedins_hilist_id")
        private String fixmedinsHilistId;

        @JSONField(name = "list_type")
        private String listType;

        @JSONField(name = "med_list_codg")
        private String medListCodg;
    }
}
