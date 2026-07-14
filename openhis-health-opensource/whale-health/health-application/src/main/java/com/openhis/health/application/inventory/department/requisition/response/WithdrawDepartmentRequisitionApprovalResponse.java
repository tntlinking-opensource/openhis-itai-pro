/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.requisition.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 撤回科室药房请领审批响应（IF-DEP-QL-07）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "撤回科室药房请领审批 IF-DEP-QL-07")
public class WithdrawDepartmentRequisitionApprovalResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 撤回后状态 SupplyStatus#getCode。 */
    @Schema(description = "撤回后状态 SupplyStatus#getCode")
    private String statusEnum;
}
