/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.deptissuereturn.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 科室退领单详情请求（IF-DEP-TL-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室退领单详情 IF-DEP-TL-03", requiredProperties = {"busNo"})
public class GetDeptIssueReturnDetailRequest {

    /** 业务单号 busNo。 */
    @NotBlank(message = "busNo 不能为空")
    @Schema(description = "业务单号 busNo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;
}
