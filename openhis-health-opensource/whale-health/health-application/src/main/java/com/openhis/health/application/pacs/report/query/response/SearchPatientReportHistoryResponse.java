/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.report.query.response;

import com.openhis.health.application.pacs.report.query.dto.PacsReportQueryRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 患者历史报告响应。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchPatientReportHistoryResponse extends PagedResult<PacsReportQueryRowDto> {
}
