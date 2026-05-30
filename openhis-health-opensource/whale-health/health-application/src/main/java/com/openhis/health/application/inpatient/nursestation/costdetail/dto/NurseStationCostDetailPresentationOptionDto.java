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

package com.openhis.health.application.inpatient.nursestation.costdetail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 费用明细展示模式选项（IF-IP-CDT-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "费用明细展示模式选项")
public class NurseStationCostDetailPresentationOptionDto {

    /** 模式 code：DETAIL / SUMMARY。 */
    @Schema(description = "展示模式 code")
    private String code;

    /** 模式展示名：明细 / 汇总。 */
    @Schema(description = "展示模式名称")
    private String display;
}
