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
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建编码系统请求（编码系统管理菜单）
 *
 */
@Data
public class CreateCodeSystemRequest {

    /** 体系机器名（唯一）。 */
    @NotBlank(message = "编码系统名称不能为空")
    @Size(max = 100, message = "编码系统名称长度不能超过100")
    private String name;

    /** 展示标题。 */
    @Size(max = 200, message = "标题长度不能超过200")
    private String title;

    /** 说明。 */
    private String description;

    /** 版本号字符串。 */
    @Size(max = 50, message = "版本号长度不能超过50")
    private String version;
}
