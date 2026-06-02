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

package com.openhis.health.application.outpatient.doctorstation.advice.response;

import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceRowDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * IF-DOR-10：加载当前就诊医嘱统一列表响应。
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code content} 为医嘱统一列表行数据。</p>
 */
public class ListDoctorStationAdviceResponse extends PagedResult<DoctorStationAdviceRowDto> {
}
