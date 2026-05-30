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

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-18：医嘱开立页初始化响应（用药频次、给药途径术语下拉）。
 * <p>费用性质下拉由 <strong>AS-IP-DOR-001</strong> 的 {@code feeNatureOptions} 提供（IF-IP-DOR-13 已废止）。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-初始化响应")
public class InitInpatientDoctorStationAdviceResponse {

    /** 用药频次术语下拉（编码体系 {@code rate_code}）。 */
    @Schema(description = "用药频次下拉")
    private List<OptionDto> medicationRateOptions;

    /** 给药途径 / 用法术语下拉（编码体系 {@code method_code}）。 */
    @Schema(description = "给药途径（用法）下拉")
    private List<OptionDto> medicationRouteOptions;
}
