/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 体温单分页窗口描述。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体温单分页窗口")
public class TemperatureSheetPaginationDto {

    /** 当前页码（0-based）。 */
    @Schema(description = "当前页码（0-based）")
    private Integer pageIndex;

    /** 总页数。 */
    @Schema(description = "总页数")
    private Integer totalPages;

    /** 日期下界。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "日期下界")
    private LocalDate dateFrom;

    /** 日期上界。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "日期上界")
    private LocalDate dateTo;
}
