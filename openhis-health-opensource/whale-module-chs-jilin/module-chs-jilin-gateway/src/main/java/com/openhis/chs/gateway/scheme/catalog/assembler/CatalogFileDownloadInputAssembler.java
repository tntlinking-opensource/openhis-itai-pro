/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.catalog.assembler;

import com.openhis.chs.gateway.scheme.catalog.model.CatFileDownloadInput;
import com.openhis.chs.domain.catalog.support.CatalogDownloadVersionSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 13xx 文件型目录下载入参组装（{@code data.ver}，infno 由调用方传入网关） */
@Component
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class CatalogFileDownloadInputAssembler {

    /**
     * 组装 13xx 文件型目录下载 input.data（仅 ver 版本号）。
     *
     * @param localVer 本地游标版本（经 {@link CatalogDownloadVersionSupport} 规范化）
     * @return 平台 input DTO */
    public CatFileDownloadInput assemble(String localVer) {
        String ver = CatalogDownloadVersionSupport.normalizeForPlatformRequest(localVer);
        CatFileDownloadInput.CatFileDownloadData data = new CatFileDownloadInput.CatFileDownloadData()
            .setVer(ver);
        CatFileDownloadInput input = new CatFileDownloadInput();
        input.setData(data);
        return input;
    }
}
