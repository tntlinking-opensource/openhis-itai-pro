/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.requisition.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 撤回科室药房请领审批请求（IF-DEP-QL-07）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "撤回科室药房请领审批 IF-DEP-QL-07", requiredProperties = {"busNo"})
public class WithdrawDepartmentRequisitionApprovalRequest {

    /** 业务单号。 */
    @NotBlank(message = "busNo 不能为空")
    @Schema(description = "业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;
}
