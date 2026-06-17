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

package com.openhis.health.application.basic.ordertemplate.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 医生站另存医嘱模板时的源行键（IF-BAS-OTL-10）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱源行键")
public class AdviceSourceLineKeyDto {

    /** 药品/诊疗/耗材请求主键。 */
    @NotNull(message = "请求主键不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "请求主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long requestId;

    /** 产品定义物理表名。 */
    @NotBlank(message = "产品定义表名不能为空")
    /** 产品定义表名。 */
    @Schema(description = "产品定义表名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productTableName;

    /** 列表稳定键（可选，用于前端去重）。 */
    @Schema(description = "列表稳定键")
    private String listRowKey;
}
