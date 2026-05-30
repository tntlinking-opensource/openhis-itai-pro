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

import java.math.BigDecimal;

/**
 * 卫宁药品目录标准化行（Gateway 输出 → 应用层 upsert）。
 *
 * <p><b>现场对接</b>：在 {@link com.openhis.health.application.integration.winning.catalog.gateway.WinningCatalogGateway}
 * 实现类中将卫宁 JSON 映射为本 DTO；{@code externalItemCode} 与 {@code busNo} 至少填一项作为幂等键。</p>
 */
@Data
@Schema(description = "卫宁药品目录行")
public class WinningMedicationCatalogItemDto {

    @Schema(description = "卫宁外部主键/物料码（推荐幂等键）")
    private String externalItemCode;

    @Schema(description = "院内业务编码 bus_no；空则回落 externalItemCode")
    private String busNo;

    @Schema(description = "药品名称")
    private String name;

    @Schema(description = "分类编码")
    private String categoryCode;

    @Schema(description = "采购单位编码")
    private String purchaseUnitCode;

    @Schema(description = "机构主键")
    private Long organizationId;

    @Schema(description = "批号（用于 LOT_NUMBER_PRICE；空则用同步默认批号）")
    private String lotNumber;

    @Schema(description = "批号售价（元）")
    private BigDecimal lotSalePrice;

    @Schema(description = "零售价（元，可选）")
    private BigDecimal retailPrice;

    @Schema(description = "采购价（元，可选）")
    private BigDecimal purchasePrice;

    @Schema(description = "财务分类 type_code")
    private String chargeTypeCode;

    @Schema(description = "医保类别 yb_type")
    private String ybType;
}
