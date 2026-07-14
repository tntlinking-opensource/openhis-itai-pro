/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.costdetail.response;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.openhis.health.application.inpatient.nursestation.costdetail.dto.NurseStationCostDetailPresentationOptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.util.List;

/**
 * IF-IP-CDT-01：费用明细页初始化响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院护士站费用明细页初始化响应")
public class LoadNurseStationCostDetailPageInitResponse {

    /** 默认业务日期下界。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "默认业务日期下界")
    private LocalDate dateFrom;

    /** 默认查询结束日期（当日）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    /** 默认业务日期上界。 */
    @Schema(description = "默认业务日期上界")
    private LocalDate dateTo;

    /** 展示模式选项。 */
    @Schema(description = "展示模式选项")
    private List<NurseStationCostDetailPresentationOptionDto> presentationOptions;

    /** 费用大类块顺序 code 列表。 */
    @Schema(description = "费用大类块顺序 code 列表")
    private List<String> expenseBlockOrder;

    /** 默认费用状态筛选 code（待结算）。 */
    @Schema(description = "默认费用状态筛选 code")
    private String defaultStatusEnum;

    /** 费用状态筛选选项。 */
    @Schema(description = "费用状态筛选选项")
    private List<NurseStationCostDetailPresentationOptionDto> statusFilterOptions;
}
