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

package com.openhis.health.application.outpatient.pharmacy.returnmedicine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 退药状态下拉选项（IF-PHA-02-01）。
 */
@Data
/** 退药状态下拉选项。 */
@Schema(description = "退药状态下拉选项")
public class RefundStatusOptionDto {

    @Schema(description = "状态编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusCode;

    @Schema(description = "状态显示名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusName;
}
