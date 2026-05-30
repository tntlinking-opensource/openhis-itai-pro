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

package com.openhis.health.application.inventory.warehouse.stockout.request;

import com.openhis.health.application.inventory.warehouse.stockout.dto.StockOutLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 *
 * <p>语义上为「同号覆盖草稿」：{@link #busNo} 标识既有出库单；装配层塌缩为 {@link CreateStockOutRequest} 后复用同一领域创建命令路径。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class UpdateStockOutRequest {

    /** 既有出库业务单号。 */
    @Schema(description = "业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 修订后源药库位置主键。 */
    @Schema(description = "源药库", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceLocationId;

    /** 修订后目的位置主键。 */
    @Schema(description = "目的位置", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

    /** 源位置类型持久化身份；规则同 {@link CreateStockOutRequest#getSourceTypeEnum()}。 */
    @Schema(description = "源位置类型", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceTypeEnum;

    /** 目的位置类型持久化身份；规则同 {@link CreateStockOutRequest#getPurposeTypeEnum()}。 */
    @Schema(description = "目的位置类型", requiredMode = Schema.RequiredMode.REQUIRED)
    private String purposeTypeEnum;

    /** 供应类别持久化身份；规则同 {@link CreateStockOutRequest#getCategoryEnum()}。 */
    @Schema(description = "供应类别", requiredMode = Schema.RequiredMode.REQUIRED)
    private String categoryEnum;

    /** 关联上游业务单号；可空。 */
    @Schema(description = "关联上游单号")
    private String originalBusNo;

    /** 经手人（医务人员主键）；修订与新建同口径。 */
    @Schema(description = "经手人 practitionerId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long practitionerId;

    /** 全量明细行（子表对齐策略由领域 SDD 约定）。 */
    @Schema(description = "明细行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<StockOutLineDto> lines;
}
