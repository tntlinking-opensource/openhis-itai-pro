/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.catalog.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.openhis.chs.gateway.scheme.common.model.wrapper.ChsDataListInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【㌳〱】目录对照上传 input 壳 */
public class Cat3301Input extends ChsDataListInput<Cat3301Input.Cat3301Row> {

    /** 3301 input.data 目录对照行 */
    @Data
    @Accessors(chain = true)
    public static class Cat3301Row {

        @JSONField(name = "fixmedins_hilist_id")
        private String fixmedinsHilistId;

        @JSONField(name = "fixmedins_hilist_name")
        private String fixmedinsHilistName;

        @JSONField(name = "list_type")
        private String listType;

        @JSONField(name = "med_list_codg")
        private String medListCodg;

        private String begndate;

        private String enddate;

        private String aprvno;

        private String spec;

        private String dosform;
    }
}
