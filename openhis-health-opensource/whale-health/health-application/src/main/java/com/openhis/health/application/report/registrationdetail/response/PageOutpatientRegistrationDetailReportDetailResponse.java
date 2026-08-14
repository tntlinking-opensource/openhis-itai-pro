/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.registrationdetail.response;

import com.openhis.health.application.report.registrationdetail.dto.OutpatientRegistrationDetailFooterDto;
import com.openhis.health.application.report.registrationdetail.dto.OutpatientRegistrationDetailRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 门诊挂号明细报表明细分页响应（IF-RPT-ORG-03）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "门诊挂号明细报表明细分页响应")
public class PageOutpatientRegistrationDetailReportDetailResponse
    extends PagedResult<OutpatientRegistrationDetailRowDto> {

    /** 同筛选挂/退页脚（对齐门诊挂号当日统计；与分页 totalCount 独立）。 */
    @Schema(description = "挂/退页脚统计")
    private OutpatientRegistrationDetailFooterDto footer;
}

