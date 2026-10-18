/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.requisition.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 新建科室药房请领单响应（IF-DEP-QL-04）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "新建科室药房请领单 IF-DEP-QL-04")
public class CreateDepartmentRequisitionResponse {

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;
}
