/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.requisition.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 科室药房请领单详情请求（IF-DEP-QL-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室药房请领单详情 IF-DEP-QL-03", requiredProperties = {"busNo"})
public class GetDepartmentRequisitionDetailRequest {

    /** 业务单号。 */
    @NotBlank(message = "busNo 不能为空")
    @Schema(description = "业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;
}
