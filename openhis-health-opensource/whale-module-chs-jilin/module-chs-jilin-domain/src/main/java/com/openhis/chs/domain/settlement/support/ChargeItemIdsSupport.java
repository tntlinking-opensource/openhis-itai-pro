/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.support;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** {@code charge_item_ids} JSON 数组序列化（兼容历史 CSV） */
public final class ChargeItemIdsSupport {

    private ChargeItemIdsSupport() {
    }

    /**
     * 将收费项 id 列表序列化为 JSON 数组字符串（去重保序）。
     *
     * @param chargeItemIds 收费项 id
     * @return {@code [1,2,3]} 或 null
     */
    public static String toJsonArray(List<Long> chargeItemIds) {
        if (chargeItemIds == null || chargeItemIds.isEmpty()) {
            return null;
        }
        JSONArray array = new JSONArray();
        Set<Long> seen = new LinkedHashSet<>();
        for (Long id : chargeItemIds) {
            if (id == null) {
                continue;
            }
            if (!seen.add(id)) {
                continue;
            }
            array.add(id);
        }
        if (array.isEmpty()) {
            return null;
        }
        return array.toJSONString();
    }

    /**
     * 解析 {@code charge_item_ids}，兼容 JSON 数组与历史逗号分隔（去重保序）。
     *
     * <p>预结算快照可能因明细展开写入重复 id；按 id 查库为集合语义，重复会导致
     * 「请求条数 ≠ 查出条数」误报收费项不完整。</p>
     *
     * @param raw 库中原始值
     * @return id 列表，无则空列表
     */
    public static List<Long> parseIds(String raw) {
        if (isEmpty(raw)) {
            return Collections.emptyList();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            JSONArray array = JSON.parseArray(trimmed);
            if (array == null || array.isEmpty()) {
                return Collections.emptyList();
            }
            Set<Long> seen = new LinkedHashSet<>();
            for (int i = 0; i < array.size(); i++) {
                Object value = array.get(i);
                if (value != null) {
                    seen.add(Long.valueOf(String.valueOf(value)));
                }
            }
            return new ArrayList<>(seen);
        }
        String[] parts = trimmed.split(",");
        Set<Long> seen = new LinkedHashSet<>();
        for (String part : parts) {
            if (isNotEmpty(part)) {
                seen.add(Long.valueOf(part.trim()));
            }
        }
        return new ArrayList<>(seen);
    }
}
