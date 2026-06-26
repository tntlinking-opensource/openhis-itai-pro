/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.chs.application.catalog.mapper;

import com.openhis.chs.api.dto.catalog.Catalog1312RowOutput;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadCursorDto;
import com.openhis.chs.domain.catalog.dto.response.NationalCatalogItemDto;
import com.openhis.chs.domain.catalog.command.UpsertNationalCatalogRowCommand;
import com.openhis.chs.domain.catalog.entity.YbCatalogDownloadCursor;
import com.openhis.chs.domain.catalog.entity.YbNationalCatalogItem;

import java.util.List;

/**
 * 国家目录 DTO / Command 映射（禁止承载业务流程判断）。
 */
public interface NationalCatalogMapper {

    /**
     * 将国家目录镜像实体映射为列表行 DTO。
     *
     * @param entity 目录镜像行；{@code null} 时返回 {@code null}
     * @return 列表行 DTO 或 {@code null}
     */
    NationalCatalogItemDto toDto(YbNationalCatalogItem entity);

    /**
     * 将目录下载游标实体映射为 DTO。
     *
     * @param entity 游标行；{@code null} 时返回 {@code null}
     * @return 游标 DTO 或 {@code null}
     */
    CatalogDownloadCursorDto toDto(YbCatalogDownloadCursor entity);

    /**
     * 将 1312 平台返回行批量转换为领域 upsert 命令。
     *
     * @param rows 平台输出；{@code null} 或空时返回空列表
     * @return upsert 命令列表
     */
    List<UpsertNationalCatalogRowCommand> toUpsertCommands(List<Catalog1312RowOutput> rows);
}
