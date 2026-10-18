/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.catalog.parser;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.gateway.scheme.catalog.model.Cat1312RowOutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.zxplat.plugin.chs.gateway.scheme.catalog.model.Cat1312RowOutput;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import com.zxplat.plugin.chs.gateway.scheme.catalog.model.Cat1312RowOutput;

/** 【1312】output 解析器 */
public final class Cat1312OutputParser {

    private Cat1312OutputParser() {
    }

    public static List<Cat1312RowOutput> parseRows(ResultBody body) {
        if (body == null || isEmpty(body.getOutput())) {
            return Collections.emptyList();
        }
        String raw = body.getOutput().trim();
        JSONArray array;
        if (raw.startsWith("[")) {
            array = JSON.parseArray(raw);
        } else {
            JSONObject root = JSON.parseObject(raw);
            array = root.getJSONArray("data");
            if (array == null) {
                array = root.getJSONArray("output");
            }
            if (array == null && root.containsKey("hilist_code")) {
                array = new JSONArray(root);
            }
        }
        if (array == null || array.isEmpty()) {
            return Collections.emptyList();
        }
        List<Cat1312RowOutput> rows = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JSONObject jo = array.getJSONObject(i);
            if (jo == null) {
                continue;
            }
            Cat1312RowOutput row = new Cat1312RowOutput();
            row.setHilistCode(jo.getString("hilist_code"));
            row.setHilistName(jo.getString("hilist_name"));
            row.setInsuAdmdvs(jo.getString("insu_admdvs"));
            row.setBegndate(jo.getString("begndate"));
            row.setEnddate(jo.getString("enddate"));
            row.setChrgitmLv(jo.getString("chrgitm_lv"));
            row.setLmtUsedFlag(jo.getString("lmt_used_flag"));
            row.setListType(jo.getString("list_type"));
            row.setPinyin(jo.getString("pinyin"));
            row.setWubi(jo.getString("wubi"));
            row.setValiFlag(jo.getString("vali_flag"));
            row.setUpdtTime(jo.getString("updt_time"));
            rows.add(row);
        }
        return rows;
    }
}
