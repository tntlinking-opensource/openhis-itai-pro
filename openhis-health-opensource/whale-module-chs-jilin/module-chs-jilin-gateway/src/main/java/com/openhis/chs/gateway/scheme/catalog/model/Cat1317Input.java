/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.catalog.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.openhis.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【1317】医药机构目录匹配信息查询 input 壳 */
public class Cat1317Input extends ChsDataInput<Cat1317Input.Cat1317Data> {

    /** 1317 input.data 节点 */
    @Data
    @Accessors(chain = true)
    public static class Cat1317Data {

        /** 查询时间点 */
        @JSONField(name = "query_date")
        private String queryDate;

        /** 定点医药机构编号 */
        @JSONField(name = "fixmedins_code")
        private String fixmedinsCode;

        /** 定点医药机构目录编号（院内目录编码） */
        @JSONField(name = "medins_list_codg")
        private String medinsListCodg;

        /** 定点医药机构目录名称 */
        @JSONField(name = "medins_list_name")
        private String medinsListName;

        /** 参保机构医保区划 */
        @JSONField(name = "insu_admdvs")
        private String insuAdmdvs;

        /** 目录类别 */
        @JSONField(name = "list_type")
        private String listType;

        /** 医疗目录编码（国家医保码） */
        @JSONField(name = "med_list_codg")
        private String medListCodg;

        /** 开始日期 */
        private String begndate;

        /** 有效标志 */
        @JSONField(name = "vali_flag")
        private String valiFlag;

        /** 更新时间（增量游标） */
        @JSONField(name = "updt_time")
        private String updtTime;

        /** 当前页数 */
        @JSONField(name = "page_num")
        private Integer pageNum;

        /** 本页数据量 */
        @JSONField(name = "page_size")
        private Integer pageSize;
    }
}
