/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissuereturn.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 撤回科室退领单审批请求（IF-DEP-TL-07）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "撤回科室退领单审批 IF-DEP-TL-07", requiredProperties = {"busNo"})
public class WithdrawDeptIssueReturnApprovalRequest {

    /** 业务单号 busNo。 */
    @NotBlank(message = "busNo 不能为空")
    @Schema(description = "业务单号 busNo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;
}
