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

package com.openhis.health.application.basic.devicecatalog.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 耗材目录分页查询请求。分页与关键字见 {@link AbstractPageQuery}（B.10.11）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PageDeviceCatalogRequest extends AbstractHealthPageRequest {

    /** 分类过滤。 */
    private String categoryCode;
    /** 耗材种类（{@code device_type_code}）过滤。 */
    private String typeCode;
}
