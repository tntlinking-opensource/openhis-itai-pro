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
 * 审批同意科室药房请领响应（IF-DEP-QL-08）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "审批同意科室药房请领 IF-DEP-QL-08")
public class AgreeDepartmentRequisitionResponse {

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;

    /** 同意后状态 SupplyStatus#getCode。 */
    @Schema(description = "同意后状态 SupplyStatus#getCode")
    private String statusEnum;
}
