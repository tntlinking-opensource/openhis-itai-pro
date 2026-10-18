/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.nurseworkload.response;

import com.zxplat.application.report.nurseworkload.dto.NurseExecutionDetailRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 护士诊疗执行明细分页出参（IF-RPT-NWL-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "护士诊疗执行明细分页 IF-RPT-NWL-03")
public class PageNurseExecutionWorkloadDetailResponse {

    /** 总行数。 */
    @Schema(description = "总行数")
    private Long total;

    /** 当前页码。 */
    @Schema(description = "当前页码")
    private Integer pageNo;

    /** 页大小。 */
    @Schema(description = "页大小")
    private Integer pageSize;

    /** 明细行。 */
    @Schema(description = "明细行")
    private List<NurseExecutionDetailRowDto> rows;
}
