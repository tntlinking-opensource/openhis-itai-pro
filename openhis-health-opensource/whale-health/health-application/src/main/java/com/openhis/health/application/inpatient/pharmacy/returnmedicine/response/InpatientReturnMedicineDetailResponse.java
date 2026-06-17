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

package com.openhis.health.application.inpatient.pharmacy.returnmedicine.response;

import com.openhis.health.application.inpatient.pharmacy.returnmedicine.dto.InpatientReturnMedicineDetailRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * 查询住院退药明细响应（IF-IPR-03）。
 */
@Data
@Accessors(chain = true)
/** 查询住院退药明细响应。 */
@Schema(description = "查询住院退药明细响应")
public class InpatientReturnMedicineDetailResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "退药明细行（逐发放行）")
    private List<InpatientReturnMedicineDetailRowDto> items;
}
