/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.report.audit.response;

import com.zxplat.plugin.pacs.application.report.audit.dto.PacsAuditRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 待审列表响应。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchPendingAuditResponse extends PagedResult<PacsAuditRowDto> {
}
