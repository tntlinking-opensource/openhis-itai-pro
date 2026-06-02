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

package com.openhis.health.application.basic.warehouse.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新建库房/药房请求。
 */
@Data
public class CreateWarehouseRequest {
    /** 库房名称。 */
    @NotBlank(message = "名称不能为空")
    private String name;

    /**
     * 前端兼容值：16/17；领域值：PHARMACY/WAREHOUSE。
     */
    @NotBlank(message = "物理形式不能为空")
    private String formEnum;
}
