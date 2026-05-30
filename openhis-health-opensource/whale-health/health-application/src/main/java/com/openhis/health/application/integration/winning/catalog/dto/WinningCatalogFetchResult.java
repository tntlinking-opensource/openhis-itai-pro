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

package com.openhis.health.application.integration.winning.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 卫宁网关拉取结果（标准化 DTO 列表 + 元数据）。
 *
 * @param <T> 目录行类型
 */
@Data
@Schema(description = "卫宁目录拉取结果")
public class WinningCatalogFetchResult<T> {

    @Schema(description = "是否为占位 Stub（未接卫宁）")
    private Boolean stubMode;

    @Schema(description = "网关说明")
    private String gatewayMessage;

    @Schema(description = "卫宁侧总条数（若接口返回）")
    private Integer remoteTotalCount;

    @Schema(description = "当前页标准化行")
    private List<T> items = new ArrayList<>();
}
