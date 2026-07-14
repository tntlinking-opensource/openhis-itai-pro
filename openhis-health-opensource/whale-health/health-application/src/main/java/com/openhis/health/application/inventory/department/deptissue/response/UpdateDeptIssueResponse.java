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
 * 编辑科室领用单响应（IF-DEP-IS-05）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "编辑科室领用单 IF-DEP-IS-05")
public class UpdateDeptIssueResponse {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;
}
