/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.config.template.response;

import com.zxplat.plugin.pacs.application.config.template.dto.PacsReportTemplateRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报告模板列表响应。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchReportTemplatesResponse extends PagedResult<PacsReportTemplateRowDto> {
}
