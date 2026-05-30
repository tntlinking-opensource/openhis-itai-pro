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

package com.openhis.health.domain.terminology.command.codesystem;

import lombok.Data;

/**
 * 更新编码系统命令
 *
 * <p>用于封装更新编码系统的领域操作参数</p>
 *
 */
@Data
public class UpdateCodeSystemCommand {

    /**
     * 编码系统ID（必填）
     */
    private Long id;

    /**
     * 编码系统名称（可选）
     */
    private String name;

    /**
     * 编码系统标题（可选）
     */
    private String title;

    /**
     * 描述（可选）
     */
    private String description;

    /**
     * 版本号（可选）
     */
    private String version;

    /**
     * 状态（可选）
     */
    private String status;
}
