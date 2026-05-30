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
 * <p>头级类型字段与明细行共同装配为领域 {@link com.openhis.health.domain.workflow.supplyrequest.command.CreateStockOutCommand}；枚举类字段一律传持久化 {@code code}（B.10.8.1）。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class CreateStockOutRequest {

    /** 源药库 {@link com.openhis.health.domain.administration.location.entity.Location} 主键。 */
    @Schema(description = "源药库", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceLocationId;

    /** 目的位置主键（药房或柜等，由领域与读模型解释）。 */
    @Schema(description = "目的位置", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

    /** 源位置类型持久化身份；可空由领域解释，非空时须为合法 {@link com.openhis.health.domain.share.enums.SupplyRequestType#getCode()}。 */
    @Schema(description = "源位置类型 SupplyRequestType#getCode", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceTypeEnum;

    /** 目的位置类型持久化身份，须为合法 {@link com.openhis.health.domain.share.enums.SupplyRequestType#getCode()}。 */
    @Schema(description = "目的位置类型", requiredMode = Schema.RequiredMode.REQUIRED)
    private String purposeTypeEnum;

    /** 供应类别持久化身份，须为合法 {@link com.openhis.health.domain.share.enums.SupplyCategory#getCode()}。 */
    @Schema(description = "供应类别 SupplyCategory#getCode", requiredMode = Schema.RequiredMode.REQUIRED)
    private String categoryEnum;

    /** 关联上游业务单号（如药房请领）；可空。 */
    @Schema(description = "关联请领等业务单号")
    private String originalBusNo;

    /** 经手人（医务人员主键）；须由前端显式选择。 */
    @Schema(description = "经手人 practitionerId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long practitionerId;

    /** 预分配业务单号；空则由领域按 B.10.9 取号。 */
    @Schema(description = "预分配业务单号，空则领域生成")
    private String busNo;

    /** 出库明细行集合，与头一并提交。 */
    @Schema(description = "明细行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<StockOutLineDto> lines;
}
