package com.openhis.chs.api.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.api.dto.catalog.CatalogFileDownloadOutput;
import com.openhis.chs.api.dto.catalog.Catalog1312RowOutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 平台 output 字符串解析（无 Spring 依赖，供 health/chs 共用）。
 */
public final class ChsOutputParser {

    private ChsOutputParser() {
    }

    /** 从 9001 应答 output 解析 {@code sign_no}。 */
    public static String parseSignNo(ResultBody body) {
        if (body == null || body.getOutput() == null || body.getOutput().isBlank()) {
            return null;
        }
        JSONObject root = JSON.parseObject(body.getOutput());
        JSONObject sign = root.getJSONObject("signinoutb");
        if (sign == null) {
            sign = root.getJSONObject("output");
        }
        if (sign == null) {
            return null;
        }
        return sign.getString("sign_no");
    }

    public static CatalogFileDownloadOutput parseCatalogFileDownloadOutput(ResultBody body) {
        if (body == null || body.getOutput() == null || body.getOutput().isBlank()) {
            return null;
        }
        JSONObject root = JSON.parseObject(body.getOutput().trim());
        if (root == null || root.isEmpty()) {
            return null;
        }
        return root.to(CatalogFileDownloadOutput.class);
    }

    public static List<Catalog1312RowOutput> parseCatalog1312Rows(ResultBody body) {
        if (body == null || body.getOutput() == null || body.getOutput().isBlank()) {
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
        List<Catalog1312RowOutput> rows = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JSONObject jo = array.getJSONObject(i);
            if (jo == null) {
                continue;
            }
            Catalog1312RowOutput row = new Catalog1312RowOutput();
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

    /** 1901 字典表 list 节点解析。 */
    public static JSONArray parse1901List(ResultBody body) {
        if (body == null || body.getOutput() == null || body.getOutput().isBlank()) {
            return new JSONArray();
        }
        String raw = body.getOutput().trim();
        if (raw.startsWith("[")) {
            return JSON.parseArray(raw);
        }
        JSONObject root = JSON.parseObject(raw);
        JSONArray list = first1901List(
            root.getJSONArray("list"),
            nested1901List(root, "output"),
            nested1901List(root, "data"),
            nested1901List(root, "result"));
        return list != null ? list : new JSONArray();
    }

    private static JSONArray nested1901List(JSONObject root, String key) {
        if (root == null || key == null) {
            return null;
        }
        JSONObject nested = root.getJSONObject(key);
        return nested != null ? nested.getJSONArray("list") : null;
    }

    private static JSONArray first1901List(JSONArray... candidates) {
        for (JSONArray candidate : candidates) {
            if (candidate != null && !candidate.isEmpty()) {
                return candidate;
            }
        }
        for (JSONArray candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }
}
