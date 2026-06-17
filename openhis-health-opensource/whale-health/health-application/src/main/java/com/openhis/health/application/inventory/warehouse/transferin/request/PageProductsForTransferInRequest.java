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

package com.openhis.health.application.inventory.warehouse.transferin.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 药房入库选品分页请求（IF-PHA-IN-11）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(
    description = "药房入库选品分页 IF-PHA-IN-11",
    requiredProperties = {"sourceLocationId", "productTableName", "pageNo", "pageSize"}
)
public class PageProductsForTransferInRequest extends AbstractHealthPageRequest {

    @NotNull(message = "sourceLocationId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 来源药库主键。 */
    @Schema(description = "来源药库主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceLocationId;

    @NotBlank(message = "productTableName 不能为空")
    /** 物品物理表 med_medication_definition / adm_device_definition。 */
    @Schema(description = "物品物理表 med_medication_definition / adm_device_definition", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productTableName;
}
