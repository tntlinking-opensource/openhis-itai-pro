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
 * 编辑科室退领单响应（IF-DEP-TL-05）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "编辑科室退领单 IF-DEP-TL-05")
public class UpdateDeptIssueReturnResponse {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;
}
