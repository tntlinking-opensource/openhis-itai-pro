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

package com.openhis.health.application.outpatient.pharmacy.dispense.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 作废门诊发放响应（IF-OPP-06）。
 */
@Data
@Accessors(chain = true)
/** 作废门诊发放响应。 */
@Schema(description = "作废门诊发放响应")
public class CancelOutpatientMedicationDispenseResponse {

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "提示文案")
    private String message;

    /** 本次作废涉及的发放单主键集合。 */
    @Schema(description = "已处理发放单主键集合")
    private List<Long> processedDispenseIds;
}
