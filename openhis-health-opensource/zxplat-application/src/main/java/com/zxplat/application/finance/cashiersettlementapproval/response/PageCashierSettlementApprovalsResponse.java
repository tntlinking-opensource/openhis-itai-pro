/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.cashiersettlementapproval.response;

import com.zxplat.application.finance.cashiersettlementapproval.dto.CashierSettlementApprovalListItemDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 交账审批列表分页响应（IF-CS-APR-02）。
 */
@Schema(description = "交账审批列表分页响应")
public class PageCashierSettlementApprovalsResponse extends PagedResult<CashierSettlementApprovalListItemDto> {
}
