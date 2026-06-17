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

package com.openhis.health.application.outpatient.pharmacy.dispense.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.SavePrepareDispenseLineSubmitDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 保存配药请求（IF-OPP-04）。
 *
 * <p>将待发记录推进为配药完成态，并携带库房、批号、追溯码与数量。</p>
 */
@Data
@Accessors(chain = true)
/** 门诊保存配药请求。 */
@Schema(description = "门诊保存配药请求")
public class SavePrepareOutpatientMedicationDispenseRequest {

    /** 就诊上下文主键。 */
    @NotNull(message = "就诊不能为空")
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 调配责任人（药师）医务主键。 */
    @NotNull(message = "调配药师不能为空")
    /** 调配药师主键。 */
    @Schema(description = "调配药师主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long preparerPractitionerId;

    /** 用户勾选的多条发放行及每行确认的数量与库位。 */
    @NotEmpty(message = "提交行不能为空")
    @Valid
    /** 勾选待发药行。 */
    @Schema(description = "勾选待发药行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<SavePrepareDispenseLineSubmitDto> lines;
}
