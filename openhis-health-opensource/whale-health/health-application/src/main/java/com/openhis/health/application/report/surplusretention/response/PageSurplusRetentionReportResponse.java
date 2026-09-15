/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.surplusretention.response;

import com.openhis.health.application.report.surplusretention.dto.SurplusRetentionReportRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 药品结余留用报表明细分页响应（IF-RPT-SRB-02）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "药品结余留用报表明细分页响应")
public class PageSurplusRetentionReportResponse extends PagedResult<SurplusRetentionReportRowDto> {
}
