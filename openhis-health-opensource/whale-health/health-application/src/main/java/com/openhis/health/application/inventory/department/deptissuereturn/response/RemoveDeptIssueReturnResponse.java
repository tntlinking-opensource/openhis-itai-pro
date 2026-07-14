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
 * 删除科室退领单响应（IF-DEP-TL-10）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "删除科室退领单 IF-DEP-TL-10")
public class RemoveDeptIssueReturnResponse {

    /** 是否成功 ok。 */
    @Schema(description = "是否成功 ok")
    private Boolean ok;
}
