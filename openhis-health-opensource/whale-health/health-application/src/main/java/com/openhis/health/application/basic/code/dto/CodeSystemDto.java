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

package com.openhis.health.application.basic.code.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 编码体系（术语系统）元数据 DTO。
 *
 */
@Data
public class CodeSystemDto {

    /** 主键。 */
    private Long id;
    /** 体系唯一名称（机器可读）。 */
    private String name;
    /** 标题/展示名。 */
    private String title;
    /** 描述。 */
    private String description;
    /** 版本号。 */
    private String version;
    /** 状态码（整型化后的持久化值）。 */
    private Integer status;
    /** 状态中文标签。 */
    private String status_dictText;
    /** 租户 ID。 */
    private Long tenantId;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
