/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissue.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 撤回科室领用审批响应（IF-DEP-IS-07）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "撤回科室领用审批 IF-DEP-IS-07")
public class WithdrawDeptIssueApprovalResponse {

    /** 是否成功 success。 */
    @Schema(description = "是否成功 success")
    private Boolean success;

    /** 撤回后状态 SupplyStatus#getCode。 */
    @Schema(description = "撤回后状态 SupplyStatus#getCode")
    private String statusEnum;
}
