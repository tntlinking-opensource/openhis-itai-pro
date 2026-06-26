/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.outpatient.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** {@code charge_item_ids} JSON 数组序列化（兼容历史 CSV）。 */
public final class ChsChargeItemIdsSupport {

    private ChsChargeItemIdsSupport() {
    }

    /**
     * 将收费项 id 列表序列化为 JSON 数组字符串。
     *
     * @param chargeItemIds 收费项 id
     * @return {@code [1,2,3]} 或 null
     */
    public static String toJsonArray(List<Long> chargeItemIds) {
        if (chargeItemIds == null || chargeItemIds.isEmpty()) {
            return null;
        }
        JSONArray array = new JSONArray();
        for (Long id : chargeItemIds) {
            if (id != null) {
                array.add(id);
            }
        }
        if (array.isEmpty()) {
            return null;
        }
        return array.toJSONString();
    }

    /**
     * 解析 {@code charge_item_ids}，兼容 JSON 数组与历史逗号分隔。
     *
     * @param raw 库中原始值
     * @return id 列表，无则空列表
     */
    public static List<Long> parseIds(String raw) {
        if (StringUtils.isBlank(raw)) {
            return Collections.emptyList();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            JSONArray array = JSON.parseArray(trimmed);
            if (array == null || array.isEmpty()) {
                return Collections.emptyList();
            }
            List<Long> ids = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) {
                Object value = array.get(i);
                if (value != null) {
                    ids.add(Long.valueOf(String.valueOf(value)));
                }
            }
            return ids;
        }
        String[] parts = trimmed.split(",");
        List<Long> ids = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (StringUtils.isNotBlank(part)) {
                ids.add(Long.valueOf(part.trim()));
            }
        }
        return ids;
    }
}
