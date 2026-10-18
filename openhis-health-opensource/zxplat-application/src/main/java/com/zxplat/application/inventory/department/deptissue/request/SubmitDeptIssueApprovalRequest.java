/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.deptissue.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 提交科室领用审批请求（IF-DEP-IS-06）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "提交科室领用审批 IF-DEP-IS-06", requiredProperties = {"busNo"})
public class SubmitDeptIssueApprovalRequest {

    /** 业务单号 busNo。 */
    @NotBlank(message = "busNo 不能为空")
    @Schema(description = "业务单号 busNo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;
}
