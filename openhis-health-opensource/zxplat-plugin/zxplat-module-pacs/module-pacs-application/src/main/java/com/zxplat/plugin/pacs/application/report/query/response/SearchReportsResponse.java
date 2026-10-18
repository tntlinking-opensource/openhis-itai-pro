/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.report.query.response;

import com.zxplat.plugin.pacs.application.report.query.dto.PacsReportQueryRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报告列表响应。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchReportsResponse extends PagedResult<PacsReportQueryRowDto> {
}
