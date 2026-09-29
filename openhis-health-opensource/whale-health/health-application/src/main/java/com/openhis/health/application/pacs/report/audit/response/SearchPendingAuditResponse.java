/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.report.audit.response;

import com.openhis.health.application.pacs.report.audit.dto.PacsAuditRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 待审列表响应。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchPendingAuditResponse extends PagedResult<PacsAuditRowDto> {
}
