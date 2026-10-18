/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.temperaturesheet.dto;

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
