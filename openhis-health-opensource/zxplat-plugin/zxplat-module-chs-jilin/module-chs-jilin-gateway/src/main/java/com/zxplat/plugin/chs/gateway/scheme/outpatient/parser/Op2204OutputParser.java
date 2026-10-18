/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.outpatient.parser;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.domain.platform.model.ChsFeeUploadResultRow;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.support.ChsPlatformResponseSupport;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** 【2204】output 校验与费用上传 result 解析 */
public final class Op2204OutputParser {

    private Op2204OutputParser() {
    }

    public static void requireSuccess(ResultBody body) {
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.OUTPATIENT_FEE_UPLOAD);
    }

    public static Map<String, ChsFeeUploadResultRow> indexByFeedetlSn(ResultBody body) {
        if (body == null || isEmpty(body.getOutput())) {
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
        Map<String, ChsFeeUploadResultRow> indexed = new HashMap<>(result.size());
        for (int i = 0; i < result.size(); i++) {
            JSONObject jo = result.getJSONObject(i);
            if (jo == null) {
                continue;
            }
            ChsFeeUploadResultRow row = jo.to(ChsFeeUploadResultRow.class);
            if (row == null) {
                continue;
            }
            String feedetlSn = row.getFeedetlSn();
            if (isEmpty(feedetlSn)) {
                feedetlSn = jo.getString("feedetl_sn");
            }
            if (isNotEmpty(feedetlSn)) {
                indexed.put(feedetlSn.trim(), row);
            }
        }
        return indexed;
    }
}
