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

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 查询编码系统请求（编码系统管理菜单）
 *
 * <p>分页见 {@link AbstractPageQuery}；综合关键字字段为 {@code keyword}。</p>
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryCodeSystemRequest extends AbstractHealthPageRequest {

    /** 按体系名称过滤。 */
    private String name;
    /** 按状态过滤（字符串码）。 */
    private String status;
}
