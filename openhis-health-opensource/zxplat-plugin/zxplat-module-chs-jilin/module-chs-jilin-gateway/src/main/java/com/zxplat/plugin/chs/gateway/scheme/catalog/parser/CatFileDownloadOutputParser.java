/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.catalog.parser;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.gateway.scheme.catalog.model.CatFileDownloadOutput;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import com.zxplat.plugin.chs.gateway.scheme.catalog.model.CatFileDownloadOutput;

/** 13xx 文件型目录下载 output 解析器 */
public final class CatFileDownloadOutputParser {

    private CatFileDownloadOutputParser() {
    }

    public static CatFileDownloadOutput parse(ResultBody body) {
        if (body == null || isEmpty(body.getOutput())) {
            return null;
        }
        JSONObject root = JSON.parseObject(body.getOutput().trim());
        if (root == null || root.isEmpty()) {
            return null;
        }
        return root.to(CatFileDownloadOutput.class);
    }
}
