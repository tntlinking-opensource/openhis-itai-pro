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

package com.openhis.health.application.inpatient.nursestation.dispensesummary.request;

import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.CreateMedicationSummaryDispenseItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 汇总保存请求（IF-IP-SUM-04）。
 */
@Getter
@Setter
@Accessors(chain = true)
/** 汇总保存 IF-IP-SUM-04。 */
@Schema(description = "汇总保存 IF-IP-SUM-04", requiredProperties = {"dispenseItems"})
public class CreateMedicationSummaryRequest {

    /** 勾选发放行（1～500）。 */
    @NotEmpty(message = "请勾选至少一条待汇总明细")
    @Valid
    /** 勾选发放行。 */
    @Schema(description = "勾选发放行", required = true)
    private List<CreateMedicationSummaryDispenseItemDto> dispenseItems;
}
