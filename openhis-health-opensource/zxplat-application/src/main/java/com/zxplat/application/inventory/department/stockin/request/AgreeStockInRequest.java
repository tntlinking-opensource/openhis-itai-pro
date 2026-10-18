/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.stockin.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 审批同意科室入库（手工单）请求（IF-DEPT-IN-08）。
 *
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "审批同意科室入库 IF-DEPT-IN-08", requiredProperties = {"busNo"})
public class AgreeStockInRequest {

    @NotBlank(message = "busNo 不能为空")
    /** 业务单号。 */
    @Schema(description = "业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 医保契约 busNo。 */
    @Schema(description = "医保契约 busNo")
    private String contractBusNo;
}
