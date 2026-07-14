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
 * 新建科室领用单响应（IF-DEP-IS-04）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "新建科室领用单 IF-DEP-IS-04")
public class CreateDeptIssueResponse {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;
}
