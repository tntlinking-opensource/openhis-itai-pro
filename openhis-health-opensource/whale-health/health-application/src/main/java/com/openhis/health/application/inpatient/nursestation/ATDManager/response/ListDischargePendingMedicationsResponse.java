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

package com.openhis.health.application.inpatient.nursestation.ATDManager.response;

import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.DischargePendingMedicationItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-NUR-ATD-04：出院前待发/待退药列表响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "出院前待发退药列表响应")
public class ListDischargePendingMedicationsResponse {

    /** 待发/待退药行。 */
    @Schema(description = "待发退药行列表")
    private List<DischargePendingMedicationItemDto> items;
}
