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

/* (C)2026 */
package com.openhis.health.application.basic.supplier.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 供应商编辑请求：在创建字段基础上增加主键与输入码。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "供应商")
public class UpdateSupplierRequest extends CreateSupplierRequest {
    /** 供应商主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "ID", example = "9876543210")
    private Long id;

    /** 拼音码（维护时回填）。 */
    @Schema(description = "拼音码", example = "abc")
    private String pyStr;
    /** 五笔码（维护时回填）。 */
    @Schema(description = "五笔码", example = "abc")
    private String wbStr;
}
