/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.outpatient.response;

import java.util.List;

import com.openhis.health.application.report.consumableusage.outpatient.dto.OutpatientConsumableUsageDetailRowDto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊耗材使用报表明细分页响应（IF-RPT-OCU-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊耗材使用报表明细分页响应")
public class PageOutpatientConsumableUsageReportDetailResponse {

    /** 当前页明细行。 */
    @Schema(description = "明细行")
    private List<OutpatientConsumableUsageDetailRowDto> records;

    /** 总行数。 */
    @Schema(description = "总行数")
    private Long total;

    /** 当前页码（1 基）。 */
    @Schema(description = "页码")
    private Integer pageNo;

    /** 页大小。 */
    @Schema(description = "页大小")
    private Integer pageSize;
}
