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

package com.openhis.health.application.outpatient.financial.registration.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 首次建档响应。
 */
@Data
@Accessors(chain = true)
/** 首次建档响应。 */
@Schema(description = "首次建档响应")
public class SavePatientForFirstRegisterResponse {

    @Schema(description = "患者ID")
    /** 患者ID（业务字段）。 */
    private Long patientId;

    /** 患者业务号。 */
    @Schema(description = "患者业务号")
    /** 患者业务号（业务字段）。 */
    private String patientBusNo;
}
