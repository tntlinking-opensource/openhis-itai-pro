/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.catalog.mapper;

import com.openhis.chs.gateway.scheme.catalog.model.Cat1312RowOutput;
import com.openhis.chs.domain.catalog.result.CatalogDownloadCursorResult;
import com.openhis.chs.domain.catalog.result.NationalCatalogItemResult;
import com.openhis.chs.domain.catalog.command.UpsertNationalCatalogRowCommand;
import com.openhis.chs.domain.catalog.entity.YbCatalogDownloadCursor;
import com.openhis.chs.domain.catalog.entity.YbNationalCatalogItem;

import java.util.List;

/**
 * 国家目录 DTO / Command 映射（禁止承载业务流程判断） */
public interface CatalogPlatformMapper {

    /**
     * 将国家目录镜像实体映射为列表示 DTO     *
     * @param entity 目录镜像行；{@code null} 时返回 {@code null}
     * @return 列表示 DTO {@code null} */
    NationalCatalogItemResult toDto(YbNationalCatalogItem entity);

    /**
     * 将目录下载游标实体映射为 DTO     *
     * @param entity 游标行；{@code null} 时返回 {@code null}
     * @return 游标 DTO {@code null} */
    CatalogDownloadCursorResult toDto(YbCatalogDownloadCursor entity);

    /**
     * 1312 平台返回行批量转换为领域 upsert 命令     *
     * @param rows 平台输出；{@code null} 或空时返回空列表
     * @return upsert 命令列表 */
    List<UpsertNationalCatalogRowCommand> toUpsertCommands(List<Cat1312RowOutput> rows);
}
