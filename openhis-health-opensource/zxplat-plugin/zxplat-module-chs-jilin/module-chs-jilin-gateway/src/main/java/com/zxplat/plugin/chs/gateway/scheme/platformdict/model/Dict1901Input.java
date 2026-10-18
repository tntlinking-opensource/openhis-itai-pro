/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.platformdict.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.zxplat.plugin.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【1901】字典查询 input 壳 */
public class Dict1901Input extends ChsDataInput<Dict1901Input.Dict1901Data> {

    /** 1901 input.data 节点 */
    @Data
    @Accessors(chain = true)
    public static class Dict1901Data {

        private String type;

        @JSONField(name = "parentValue")
        private String parentValue;

        private String admdvs;

        private String date;

        @JSONField(name = "Vali_Flag")
        private String valiFlag;

        @JSONField(name = "page_num")
        private Integer pageNum;

        @JSONField(name = "page_size")
        private Integer pageSize;
    }
}
