/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.insurancefeedetail.inpatient.response;

import com.openhis.health.application.report.insurancefeedetail.dto.InsuranceFeeDetailFooterDto;
import com.openhis.health.application.report.insurancefeedetail.dto.InsuranceFeeDetailRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 住院医保费用明细分页响应（含页脚四列合计）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "住院医保费用明细分页响应")
public class PageInpatientInsuranceFeeDetailReportResponse
    extends PagedResult<InsuranceFeeDetailRowDto> {

    /** 同筛选全量四列金额合计。 */
    @Schema(description = "页脚合计")
    private InsuranceFeeDetailFooterDto footer;
}
