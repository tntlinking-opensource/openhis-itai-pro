/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.platformdict.assembler;

import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.domain.share.constants.PlatformDictConstants;

import java.time.LocalDate;

/** 【1901】字典查询 input 组装 */
public final class Dict1901InputAssembler {

    private Dict1901InputAssembler() {
    }

    public static JSONObject assemblePage(
            String dictType,
            String queryAdmdvs,
            String parentValue,
            int pageNum,
            int pageSize,
            String valiFlagKey) {
        String effectiveValiFlagKey = valiFlagKey != null && !valiFlagKey.isBlank()
            ? valiFlagKey.trim()
            : PlatformDictConstants.VALI_FLAG_KEY;
        JSONObject data = new JSONObject();
        data.put("type", dictType);
        data.put("parentValue", parentValue == null ? "" : parentValue);
        data.put("admdvs", queryAdmdvs);
        data.put("date", LocalDate.now().toString());
        data.put(effectiveValiFlagKey, "1");
        data.put("page_num", pageNum);
        data.put("page_size", pageSize);
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }
}
