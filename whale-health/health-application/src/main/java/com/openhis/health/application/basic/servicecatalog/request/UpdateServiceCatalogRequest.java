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
package com.openhis.health.application.basic.servicecatalog.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 诊疗目录更新请求：在 {@link CreateServiceCatalogRequest} 全部可编辑字段基础上增加主键。
 */
@Data
@EqualsAndHashCode(callSuper = false)
/**
 * UpdateServiceCatalogRequest。
 */
public class UpdateServiceCatalogRequest extends CreateServiceCatalogRequest {
    @Schema(description = "主键ID", example = "10001", type = "Long")
    @JsonSerialize(using = ToStringSerializer.class)
    /**
     * id。
     */
    private Long id;
}
