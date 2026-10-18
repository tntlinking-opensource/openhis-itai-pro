/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.report.response;

import com.zxplat.plugin.peis.application.report.dto.PrintableReportRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 可打印报告列表分页响应（IF-PEIS-REP-01）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchPrintableResponse extends PagedResult<PrintableReportRowDto> {
}
