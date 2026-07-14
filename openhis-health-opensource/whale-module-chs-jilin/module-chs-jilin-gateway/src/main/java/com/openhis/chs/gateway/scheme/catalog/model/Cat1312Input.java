/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.catalog.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.openhis.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【1312】医保目录信息查询 input 壳 */
public class Cat1312Input extends ChsDataInput<Cat1312Input.Cat1312Data> {

    /** 1312 input.data 节点 */
    @Data
    @Accessors(chain = true)
    public static class Cat1312Data {

        @JSONField(name = "insu_admdvs")
        private String insuAdmdvs;

        @JSONField(name = "list_type")
        private String listType;

        @JSONField(name = "hilist_code")
        private String hilistCode;

        @JSONField(name = "hilist_name")
        private String hilistName;

        private String pinyin;

        @JSONField(name = "vali_flag")
        private String valiFlag;

        @JSONField(name = "chrgitm_lv")
        private String chrgitmLv;

        @JSONField(name = "lmt_used_flag")
        private String lmtUsedFlag;

        @JSONField(name = "query_date")
        private String queryDate;

        private String begndate;

        @JSONField(name = "updt_time")
        private String updtTime;

        @JSONField(name = "page_num")
        private Integer pageNum;

        @JSONField(name = "page_size")
        private Integer pageSize;
    }
}
