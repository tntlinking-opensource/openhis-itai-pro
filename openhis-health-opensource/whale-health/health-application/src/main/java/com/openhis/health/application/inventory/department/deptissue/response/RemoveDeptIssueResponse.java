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
 * 删除科室领用单响应（IF-DEP-IS-10）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "删除科室领用单 IF-DEP-IS-10")
public class RemoveDeptIssueResponse {

    /** 是否成功 success。 */
    @Schema(description = "是否成功 success")
    private Boolean success;
}
