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
 * 审批驳回科室领用单请求（IF-DEP-IS-09）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "审批驳回科室领用 IF-DEP-IS-09", requiredProperties = {"busNo"})
public class RejectDeptIssueRequest {

    /** 业务单号 busNo。 */
    @NotBlank(message = "busNo 不能为空")
    @Schema(description = "业务单号 busNo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 驳回原因码 changeReasonCode，可空。 */
    @Schema(description = "驳回原因码 changeReasonCode，可空")
    private String changeReasonCode;

    /** 驳回原因 changeReason，可空。 */
    @Schema(description = "驳回原因 changeReason，可空")
    private String changeReason;
}
