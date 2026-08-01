/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.catalog.parser;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.gateway.scheme.catalog.model.Cat1317RowOutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 【1317】output 解析器：兼容老插件 {@code {data,pages,recordCounts}}、数组根、单对象。
 */
public final class Cat1317OutputParser {

    private Cat1317OutputParser() {
    }

    /** 1317 单页解析结果（含平台分页元数据）。 */
    public static final class Page {
        private final List<Cat1317RowOutput> rows;
        private final Integer pages;
        private final Integer recordCounts;

        public Page(List<Cat1317RowOutput> rows, Integer pages, Integer recordCounts) {
            this.rows = rows == null ? List.of() : rows;
            this.pages = pages;
            this.recordCounts = recordCounts;
        }

        public List<Cat1317RowOutput> rows() {
            return rows;
        }

        public Integer pages() {
            return pages;
        }

        public Integer recordCounts() {
            return recordCounts;
        }
    }

    /**
     * 从平台 {@link ResultBody#getOutput()} 解析匹配行列表。
     *
     * @param body 平台响应体
     * @return 行列表；无数据时空列表（非 null）
     */
    public static List<Cat1317RowOutput> parseRows(ResultBody body) {
        return parsePage(body).rows();
    }

    /**
     * 解析行 + {@code pages}/{@code recordCounts}（老吉林插件同结构）。
     *
     * @param body 平台响应体
     * @return 单页结果
     */
    public static Page parsePage(ResultBody body) {
        if (body == null || isEmpty(body.getOutput())) {
            return new Page(Collections.emptyList(), null, null);
        }
        String raw = body.getOutput().trim();
        JSONArray array;
        Integer pages = null;
        Integer recordCounts = null;
        if (raw.startsWith("[")) {
            array = JSON.parseArray(raw);
        } else {
            JSONObject root = JSON.parseObject(raw);
            array = root.getJSONArray("data");
            if (array == null) {
                array = root.getJSONArray("output");
            }
            pages = root.getInteger("pages");
            recordCounts = root.getInteger("recordCounts");
            if (recordCounts == null) {
                recordCounts = root.getInteger("record_counts");
            }
            // 单条对象时按一行解析
            if (array == null && root.containsKey("medins_list_codg")) {
                array = new JSONArray();
                array.add(root);
            }
        }
        if (array == null || array.isEmpty()) {
            return new Page(Collections.emptyList(), pages, recordCounts);
        }
        List<Cat1317RowOutput> rows = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JSONObject jo = array.getJSONObject(i);
            if (jo == null) {
                continue;
            }
            rows.add(mapRow(jo));
        }
        return new Page(rows, pages, recordCounts);
    }

    /**
     * 将平台 JSON 对象映射为行 DTO。
     *
     * @param jo 单行 JSON
     * @return 行 DTO
     */
    private static Cat1317RowOutput mapRow(JSONObject jo) {
        Cat1317RowOutput row = new Cat1317RowOutput();
        row.setFixmedinsCode(jo.getString("fixmedins_code"));
        row.setMedinsListCodg(jo.getString("medins_list_codg"));
        row.setMedinsListName(jo.getString("medins_list_name"));
        row.setInsuAdmdvs(jo.getString("insu_admdvs"));
        row.setListType(jo.getString("list_type"));
        row.setMedListCodg(jo.getString("med_list_codg"));
        row.setBegndate(jo.getString("begndate"));
        row.setEnddate(jo.getString("enddate"));
        row.setAprvno(jo.getString("aprvno"));
        row.setDosform(jo.getString("dosform"));
        row.setExctCont(jo.getString("exct_cont"));
        row.setItemCont(jo.getString("item_cont"));
        row.setPrcunt(jo.getString("prcunt"));
        row.setSpec(jo.getString("spec"));
        row.setPacspec(jo.getString("pacspec"));
        row.setMemo(jo.getString("memo"));
        row.setValiFlag(jo.getString("vali_flag"));
        row.setRid(jo.getString("rid"));
        row.setUpdtTime(jo.getString("updt_time"));
        row.setCrterId(jo.getString("crter_id"));
        row.setCrterName(jo.getString("crter_name"));
        row.setCrteTime(jo.getString("crte_time"));
        row.setCrteOptinsNo(jo.getString("crte_optins_no"));
        row.setOpterId(jo.getString("opter_id"));
        row.setOpterName(jo.getString("opter_name"));
        row.setOptTime(jo.getString("opt_time"));
        row.setOptinsNo(jo.getString("optins_no"));
        row.setPoolareaNo(jo.getString("poolarea_no"));
        return row;
    }
}
