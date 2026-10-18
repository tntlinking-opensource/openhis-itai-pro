/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.approval.response;

import com.zxplat.application.finance.approval.dto.FinanceApprovalDocumentRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 财务审批单据分页响应（IF-APR-02）。
 */
@Schema(description = "财务审批单据分页响应")
public class PageFinanceApprovalDocumentResponse extends PagedResult<FinanceApprovalDocumentRowDto> {
}
