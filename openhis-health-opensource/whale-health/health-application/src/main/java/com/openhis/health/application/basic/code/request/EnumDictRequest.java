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
 * 枚举字典查询请求（前端左侧枚举TAB使用）
 */
@Data
public class EnumDictRequest {

    /** 枚举简单类名（须落在术语枚举扫描包内）。 */
    @NotBlank(message = "枚举名称不能为空")
    private String enumName;
}
