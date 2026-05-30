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

package com.openhis.health.domain.workflow.supplyrequest.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 按业务单号精确查询供应请求（同 {@code bus_no} 可能多行，一行一条明细）。
 *
 * <p>入参 {@code busNo} 由读服务 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService#getSupplyByBusNo} 侧 {@code trim}。</p>
 */
@Getter
@Setter
public class SupplyByBusNoQuery {

    /** 业务单号 {@code wkf_supply_request.bus_no}。 */
    private String busNo;
}
