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

package com.openhis.health.application.basic.code.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 根据编码系统名称与编码获取显示名请求（多参合并为单参）
 *
 */
@Data
public class GetDisplayNameRequest {

    /** 编码体系 name（机器名）。 */
    @NotBlank(message = "编码系统名称不能为空")
    private String codeSystemName;

    /** 概念编码。 */
    @NotBlank(message = "编码不能为空")
    private String code;
}
