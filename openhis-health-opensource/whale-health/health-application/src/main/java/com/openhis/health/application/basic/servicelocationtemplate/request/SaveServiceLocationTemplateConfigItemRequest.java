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

package com.openhis.health.application.basic.servicelocationtemplate.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

/**
 * 整科覆盖保存中的单条取药规则行。
 */
@Data
@Schema(description = "取药规则配置行", requiredProperties = {
    "itemTable", "distributionCategoryCode", "defLocationId", "priority"
})
public class SaveServiceLocationTemplateConfigItemRequest {

    /** 物品表名（{@code med_medication_definition} 或 {@code adm_device_definition}）。 */
    @NotBlank(message = "物品表名不能为空")
    @Schema(description = "物品表名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemTable;

    /** 发放类别编码（须与目录 categoryCode 一致）。 */
    @NotBlank(message = "发放类别不能为空")
    /** 发放类别编码。 */
    @Schema(description = "发放类别编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String distributionCategoryCode;

    /** 默认发药库位标识。 */
    @NotNull(message = "默认发药位置不能为空")
    /** 默认发药库位标识。 */
    @Schema(description = "默认发药库位标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long defLocationId;

    /** 具体物品定义标识；空表示通配。 */
    @Schema(description = "具体物品定义标识")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long itemDefinitionId;

    /** 用法编码；空表示不限用法。 */
    @Schema(description = "用法编码")
    private String methodCode;

    /** 生效开始时间；空表示全天。 */
    @Schema(description = "生效开始时间")
    private LocalTime startTime;

    /** 生效结束时间；空表示全天。 */
    @Schema(description = "生效结束时间")
    private LocalTime endTime;

    /** 优先级，从 1 起连续，数值越小越优先。 */
    @NotNull(message = "优先级不能为空")
    /** 优先级。 */
    @Schema(description = "优先级", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer priority;

    /** 展示顺序。 */
    @Schema(description = "展示顺序")
    private Integer displayOrder;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;
}
