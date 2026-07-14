/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissuereturn.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 撤回科室退领单审批响应（IF-DEP-TL-07）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "撤回科室退领单审批 IF-DEP-TL-07")
public class WithdrawDeptIssueReturnApprovalResponse {

    /** 是否成功 success。 */
    @Schema(description = "是否成功 success")
    private Boolean success;

    /** 状态 statusEnum。 */
    @Schema(description = "状态 statusEnum")
    private String statusEnum;
}
