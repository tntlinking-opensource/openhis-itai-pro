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

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 查询汇总单合并明细请求（IF-IP-SUM-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
/** 查询汇总单合并明细 IF-IP-SUM-03。 */
@Schema(description = "查询汇总单合并明细 IF-IP-SUM-03", requiredProperties = {"summaryNo"})
public class GetMedicationSummarySlipDetailRequest {

    /** 汇总单号 / busNo。 */
    @NotBlank(message = "汇总单号不能为空")
    @Schema(description = "汇总单号", required = true)
    private String summaryNo;
}
