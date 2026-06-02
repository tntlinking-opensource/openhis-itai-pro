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

package com.openhis.health.domain.workflow.supplydelivery.queries;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 按上游供应请求行主键集合批量查询发放记录（出库/入库发放等）。
 */
@Getter
@Setter
public class SupplyDeliveryRequestIdListQuery {

    /** 供应请求行主键 {@code request_id} 列表。 */
    private List<Long> requestIdList;
}
