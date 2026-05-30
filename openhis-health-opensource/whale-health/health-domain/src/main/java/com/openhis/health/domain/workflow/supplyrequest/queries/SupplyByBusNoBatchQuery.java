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

import java.util.List;

/**
 * 按多个业务单号批量查询供应请求（一览、批量门禁等）。
 */
@Getter
@Setter
public class SupplyByBusNoBatchQuery {

    /** 业务单号列表；与仓储 {@code findByBusNoIn} 一致。 */
    private List<String> busNoList;
}
