/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.upload.medcas.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 4101A 平台出参解析（health 侧，避免依赖 chs-bridge）。
 */
public final class SettleListOutputSupport {

    /**
     * 禁止实例化：仅静态解析方法。
     */
    private SettleListOutputSupport() {
    }

    /**
     * 从平台 output 解析 setl_list_id。
     *
     * @param output 平台 output 原文
     * @return 清单流水号，解析失败时 null
     */
    public static String parseSetlListId(String output) {
        if (trimToNull(output) == null) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(output);
            if (root == null) {
                return null;
            }
            // 根级 setl_list_id
            String direct = trimToNull(root.getString("setl_list_id"));
            if (direct != null) {
                return direct;
            }
            Object data = root.get("data");
            // data 为对象：{data:{setl_list_id}}
            if (data instanceof JSONObject dataObj) {
                return trimToNull(dataObj.getString("setl_list_id"));
            }
            // data 为字符串再解析一层
            if (data instanceof String dataStr) {
                JSONObject nested = JSON.parseObject(dataStr);
                if (nested != null) {
                    return trimToNull(nested.getString("setl_list_id"));
                }
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }
}
