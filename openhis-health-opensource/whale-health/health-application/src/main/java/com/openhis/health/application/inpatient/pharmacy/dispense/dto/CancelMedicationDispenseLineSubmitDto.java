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

package com.openhis.health.application.inpatient.pharmacy.dispense.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院作废发放提交行（IF-IPP-06）。
 */
@Data
@Accessors(chain = true)
/** 住院作废发放提交行。 */
@Schema(description = "住院作废发放提交行")
public class CancelMedicationDispenseLineSubmitDto {

    @Schema(description = "发放单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /** 未执行原因 code（落库 {@code not_performed_reason_enum}）。 */
    @Schema(description = "作废原因 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String notPerformedReasonEnum;
}
