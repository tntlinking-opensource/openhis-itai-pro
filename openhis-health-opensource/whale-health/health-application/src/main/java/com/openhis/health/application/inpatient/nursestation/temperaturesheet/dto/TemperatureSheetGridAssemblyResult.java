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

package com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 体温单网格与派生元数据装配结果（IF-IP-TMP-02 · SDD §3.2 步骤 7）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体温单网格装配结果")
public class TemperatureSheetGridAssemblyResult {

    /** 日×6 槽宽表行。 */
    @Schema(description = "网格行")
    private List<TemperatureSheetGridRowDto> gridRows = new ArrayList<>();

    /** 住院天数/周次/术后天数派生元数据。 */
    @Schema(description = "派生元数据")
    private TemperatureSheetDerivedMetaDto derivedMeta;
}
