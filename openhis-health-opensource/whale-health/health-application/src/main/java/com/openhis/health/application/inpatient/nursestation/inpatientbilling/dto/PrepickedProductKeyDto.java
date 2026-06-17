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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-PRC-01：预选目录项（product_definition_id + product_table_name）。
 *
 * <p>组套 IF-08 预览、历史行 hydration 等快捷入口以 ids 收窄目录分页，与 IF-DOR-11
 * {@code prepickedProductKeys} 同套路；读侧返回 {@code defaultDispenseLocationId}，写 IF-02/04 回传
 * {@code performLocation}。</p>
 */
@Data
@Accessors(chain = true)
/** 护士划价选品目录预选产品键。 */
@Schema(description = "护士划价选品目录预选产品键")
public class PrepickedProductKeyDto {

    /** 产品定义主键。 */
    @NotNull(message = "产品定义主键不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "产品定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long productDefinitionId;

    /** 产品来源表名。 */
    @NotBlank(message = "产品来源表名不能为空")
    /** 产品来源表名。 */
    @Schema(description = "产品来源表名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productTableName;
}
