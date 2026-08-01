/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.approval.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 初始化财务审批页请求（IF-APR-01）。
 *
 * <p>允许空体；忽略客户端传入的默认 type/status（由前端自带默认：类型空=全部、状态待审）。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "初始化财务审批页请求")
public class InitFinanceApprovalRequest {
}
