/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.approval.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 财务审批驳回请求（IF-APR-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "财务审批驳回请求", requiredProperties = {"busNo", "documentKind"})
public class RejectFinanceApprovalRequest {

    /** 业务单号。 */
    @NotBlank(message = "单号不能为空")
    @Schema(description = "业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 文档种类：SUPPLY / PRICE_ADJUSTMENT。 */
    @NotBlank(message = "documentKind 不能为空")
    @Schema(description = "文档种类 SUPPLY/PRICE_ADJUSTMENT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String documentKind;

    /**
     * 供应类型 code；{@code documentKind=SUPPLY} 时必填且须∈财务供应白名单。
     */
    @Schema(description = "供应类型 code；SUPPLY 时必填")
    private String typeEnum;

    /**
     * 驳回原因；调价必填；供应建议必填以便财务留痕。
     */
    @Schema(description = "驳回原因；调价必填")
    private String rejectReason;
}
