/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.settlement.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.ResultBody;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** 2204/2301 费用上传平台 result 解析（领域层）。 */
public final class FeeUploadResultIndexer {

    private FeeUploadResultIndexer() {
    }

    /**
     * 解析 2204/2301 输出 result 数组，按 feedetl_sn 索引。
     */
    public static Map<String, JSONObject> indexByFeedetlSn(ResultBody body) {
        if (body == null || StringUtils.isBlank(body.getOutput())) {
            return Collections.emptyMap();
        }
        JSONObject output = JSON.parseObject(body.getOutput());
        if (output == null) {
            return Collections.emptyMap();
        }
        JSONArray result = output.getJSONArray("result");
        if (result == null || result.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, JSONObject> indexed = new HashMap<>(result.size());
        for (int i = 0; i < result.size(); i++) {
            JSONObject row = result.getJSONObject(i);
            if (row == null) {
                continue;
            }
            String feedetlSn = row.getString("feedetl_sn");
            if (StringUtils.isNotBlank(feedetlSn)) {
                indexed.put(feedetlSn.trim(), row);
            }
        }
        return indexed;
    }

    /**
     * 从平台 result 行读取金额（无依赖 application 层工具）。
     */
    public static BigDecimal decimal(JSONObject row, String key) {
        if (row == null || StringUtils.isBlank(key) || !row.containsKey(key)) {
            return null;
        }
        Object raw = row.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal decimal) {
            return decimal;
        }
        if (raw instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        return new BigDecimal(text);
    }
}
