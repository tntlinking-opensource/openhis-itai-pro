/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.assembler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.dto.catalog.CatalogFileDownloadInput;
import com.openhis.chs.domain.catalog.support.CatalogDownloadVersionSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 13xx 文件型目录下载入参组装（{@code data.ver}，infno 由调用方传入网关）。
 */
@Component
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class CatalogFileDownloadInputAssembler {

    /**
     * 组装 13xx 文件型目录下载 input.data（仅 ver 版本号）。
     *
     * @param localVer 本地游标版本（经 {@link CatalogDownloadVersionSupport} 规范化）
     * @return 平台 input JSON
     */
    public JSONObject assemble(String localVer) {
        String ver = CatalogDownloadVersionSupport.normalizeForPlatformRequest(localVer);
        CatalogFileDownloadInput data = CatalogFileDownloadInput.builder().ver(ver).build();
        JSONObject root = new JSONObject();
        root.put("data", JSON.toJSON(data));
        return root;
    }
}
