/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.report.response;

import com.openhis.health.application.peis.report.dto.PrintableReportRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 可打印报告列表分页响应（IF-PEIS-REP-01）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchPrintableResponse extends PagedResult<PrintableReportRowDto> {
}
