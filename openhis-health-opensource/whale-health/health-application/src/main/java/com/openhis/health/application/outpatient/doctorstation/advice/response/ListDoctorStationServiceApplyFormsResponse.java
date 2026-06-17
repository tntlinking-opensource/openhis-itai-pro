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

package com.openhis.health.application.outpatient.doctorstation.advice.response;

import com.openhis.health.application.outpatient.doctorstation.advice.dto.ServiceApplyFormHeaderDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-22 / IF-IP-DOR-24：申请单列表响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "申请单列表响应")
public class ListDoctorStationServiceApplyFormsResponse {

    /** 按 {@code prescriptionNo} 分组的申请单头列表。 */
    @Schema(description = "按 prescriptionNo 分组的申请单")
    private List<ServiceApplyFormHeaderDto> forms;
}
