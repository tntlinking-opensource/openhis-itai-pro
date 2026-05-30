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

package com.openhis.health.domain.terminology.query;

import com.openhis.health.domain.share.enums.CodeSystemStatus;
import lombok.Data;

/**
 * 编码系统查询条件
 *
 * <p>用于封装查询编码系统的领域查询参数</p>
 *
 */
@Data
public class CodeSystemQuery {

    /**
     * 关键词（搜索名称、标题）
     */
    private String keyword;

    /**
     * 编码系统名称（精确匹配）
     */
    private String name;

    /**
     * 状态
     */
    private CodeSystemStatus status;
}
