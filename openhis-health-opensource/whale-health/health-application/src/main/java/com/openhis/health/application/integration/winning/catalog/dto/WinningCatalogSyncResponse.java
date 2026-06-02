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
 * 卫宁目录同步结果摘要。
 */
@Data
@Schema(description = "卫宁目录同步响应")
public class WinningCatalogSyncResponse {

    @Schema(description = "是否执行成功（无致命错误）")
    private Boolean success;

    @Schema(description = "产品种类：MEDICATION | DEVICE | ACTIVITY | ALL")
    private String productKind;

    @Schema(description = "网关是否为占位 Stub（未接卫宁）")
    private Boolean stubMode;

    @Schema(description = "网关/编排说明")
    private String message;

    @Schema(description = "卫宁返回总行数")
    private Integer fetchedCount;

    @Schema(description = "新建条数")
    private Integer createdCount;

    @Schema(description = "更新条数")
    private Integer updatedCount;

    @Schema(description = "失败条数")
    private Integer failedCount;

    @Schema(description = "跳过条数（如未开启覆盖）")
    private Integer skippedCount;

    @Schema(description = "定价行刷新条数")
    private Integer pricingAppliedCount;

    @Schema(description = "行级失败明细")
    private List<WinningCatalogSyncFailureRow> failures = new ArrayList<>();
}
