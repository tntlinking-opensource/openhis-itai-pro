/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.temperaturesheet.dto;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 体温单日行（含 6 个三测槽单元）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体温单日行")
public class TemperatureSheetGridRowDto {

    /** 业务日期。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "业务日期")
    private LocalDate recordDate;

    /** 当日 6 槽单元。 */
    @Schema(description = "当日 6 槽单元")
    private List<TemperatureSheetGridSlotCellDto> slotCells = new ArrayList<>();
}
