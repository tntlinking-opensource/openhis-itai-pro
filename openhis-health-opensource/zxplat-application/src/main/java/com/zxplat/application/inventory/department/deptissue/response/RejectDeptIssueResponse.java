/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.deptissue.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 审批驳回科室领用单响应（IF-DEP-IS-09）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "审批驳回科室领用 IF-DEP-IS-09")
public class RejectDeptIssueResponse {

    /** 是否成功 success。 */
    @Schema(description = "是否成功 success")
    private Boolean success;
}
