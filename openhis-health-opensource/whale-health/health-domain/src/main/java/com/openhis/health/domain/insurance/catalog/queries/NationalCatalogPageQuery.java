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

package com.openhis.health.domain.insurance.catalog.queries;

import lombok.Data;

/**
 * 国家医保目录镜像分页查询入参。
 */
@Data
public class NationalCatalogPageQuery {

    private Long contractId;
    private Long tenantId;
    private String listType;
    private String keyword;
    private String hilistCode;
    private int pageNo = 1;
    private int pageSize = 20;
}
