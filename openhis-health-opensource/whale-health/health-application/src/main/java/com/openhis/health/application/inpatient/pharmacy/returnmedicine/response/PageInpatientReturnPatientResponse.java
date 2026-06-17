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

package com.openhis.health.application.inpatient.pharmacy.returnmedicine.response;

import com.openhis.health.application.inpatient.pharmacy.returnmedicine.dto.InpatientReturnPatientRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 分页查询住院退药患者队列响应（IF-IPR-02）。
 */
/** 分页查询住院退药患者队列响应。 */
@Schema(description = "分页查询住院退药患者队列响应")
public class PageInpatientReturnPatientResponse extends PagedResult<InpatientReturnPatientRowDto> {

    private static final long serialVersionUID = 1L;
}
