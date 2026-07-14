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
 * 驳回科室退领单请求（IF-DEP-TL-09）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "驳回科室退领单 IF-DEP-TL-09", requiredProperties = {"busNo"})
public class RejectDeptIssueReturnRequest {

    /** 业务单号 busNo。 */
    @NotBlank(message = "busNo 不能为空")
    @Schema(description = "业务单号 busNo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 驳回原因码 changeReasonCode。 */
    @Schema(description = "驳回原因码 changeReasonCode")
    private String changeReasonCode;

    /** 驳回原因 changeReason。 */
    @Schema(description = "驳回原因 changeReason")
    private String changeReason;
}
