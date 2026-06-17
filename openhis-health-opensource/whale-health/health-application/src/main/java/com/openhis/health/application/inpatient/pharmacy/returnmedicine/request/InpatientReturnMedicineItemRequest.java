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

package com.openhis.health.application.inpatient.pharmacy.returnmedicine.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 住院确认退药单项请求（IF-IPR-04）。
 */
@Data
/** 住院确认退药单项请求。 */
@Schema(description = "住院确认退药单项请求", requiredProperties = {"dispenseId"})
public class InpatientReturnMedicineItemRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "待退发放行主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "dispenseId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;
}
