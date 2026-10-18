/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.deptissuereturn.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 驳回科室退领单响应（IF-DEP-TL-09）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "驳回科室退领单 IF-DEP-TL-09")
public class RejectDeptIssueReturnResponse {

    /** 是否成功 ok。 */
    @Schema(description = "是否成功 ok")
    private Boolean ok;
}
