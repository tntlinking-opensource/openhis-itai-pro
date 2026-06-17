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

package com.openhis.health.application.inpatient.doctorstation.encountermanage.response;

import com.openhis.health.application.inpatient.doctorstation.encountermanage.dto.DoctorStationPatientQueueItemDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * 住院医生站在院患者列表分页响应（AS-IP-DOR-001 / IF-IP-DOR-01）。
 *
 * <p>继承 {@link com.openhis.whale.core.application.dto.PagedResult}，对外字段为
 * {@code content}、{@code totalCount}、{@code pageNo}（1-based）、{@code pageSize}（B.10.11）；
 * 须经 {@link com.openhis.health.module.common.pagination.PagedResultBuilder} 装配。</p>
 */
public class PagePatientQueueResponse extends PagedResult<DoctorStationPatientQueueItemDto> {
}
