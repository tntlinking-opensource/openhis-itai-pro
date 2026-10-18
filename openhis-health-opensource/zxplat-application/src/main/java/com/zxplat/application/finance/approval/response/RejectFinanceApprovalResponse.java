/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.approval.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 财务审批驳回响应（IF-APR-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "财务审批驳回响应")
public class RejectFinanceApprovalResponse {

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;

    /** 文档种类。 */
    @Schema(description = "文档种类")
    private String documentKind;

    /** 驳回后状态码。 */
    @Schema(description = "驳回后状态码")
    private String statusEnum;
}
