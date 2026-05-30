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
 * 卫宁诊疗目录标准化行。
 */
@Data
@Schema(description = "卫宁诊疗目录行")
public class WinningActivityCatalogItemDto {

    private String externalItemCode;

    private String busNo;

    private String name;

    private String categoryCode;

    private String packageUnitCode;

    private Long organizationId;

    private Long locationId;

    @Schema(description = "零售价 SALE（元）")
    private BigDecimal retailPrice;

    private String chargeTypeCode;

    private String ybType;

    private String typeEnum;

    private String statusEnum;
}
