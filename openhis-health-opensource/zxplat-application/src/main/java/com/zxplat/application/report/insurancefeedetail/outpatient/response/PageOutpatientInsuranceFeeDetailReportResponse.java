/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.insurancefeedetail.outpatient.response;

import com.zxplat.application.report.insurancefeedetail.dto.InsuranceFeeDetailFooterDto;
import com.zxplat.application.report.insurancefeedetail.dto.InsuranceFeeDetailRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 门诊医保费用明细分页响应（含页脚四列合计）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "门诊医保费用明细分页响应")
public class PageOutpatientInsuranceFeeDetailReportResponse
    extends PagedResult<InsuranceFeeDetailRowDto> {

    /** 同筛选全量四列金额合计。 */
    @Schema(description = "页脚合计")
    private InsuranceFeeDetailFooterDto footer;
}
