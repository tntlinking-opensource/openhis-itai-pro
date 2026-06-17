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

package com.openhis.health.application.inpatient.doctorstation.advice.response;

import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceRowDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * IF-IP-DOR-22：住院医生站医嘱分页查询响应（全类型统一列表行，与 IF-IP-DOR-10 同构）。
 */
public class PageInpatientDoctorStationAdviceResponse extends PagedResult<InpatientDoctorStationAdviceRowDto> {
}
