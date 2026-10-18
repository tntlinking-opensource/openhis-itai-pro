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
 * 审批同意科室领用单响应（IF-DEP-IS-08）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "审批同意科室领用 IF-DEP-IS-08")
public class AgreeDeptIssueResponse {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 同意后状态 SupplyStatus#getCode。 */
    @Schema(description = "同意后状态 SupplyStatus#getCode")
    private String statusEnum;
}
