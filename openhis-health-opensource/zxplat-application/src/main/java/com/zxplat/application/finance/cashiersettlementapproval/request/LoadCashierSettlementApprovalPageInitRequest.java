/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.cashiersettlementapproval.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 交账审批页初始化请求（IF-CS-APR-01）。
 *
 * <p>允许空体。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "交账审批页初始化请求")
public class LoadCashierSettlementApprovalPageInitRequest {
}
