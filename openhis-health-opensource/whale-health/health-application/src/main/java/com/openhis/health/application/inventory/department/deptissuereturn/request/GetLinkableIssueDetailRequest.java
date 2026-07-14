/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissuereturn.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 拉取领用明细预填请求（IF-DEP-TL-13）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(
    description = "拉取领用明细预填 IF-DEP-TL-13",
    requiredProperties = {"originalBusNo", "purposeLocationId"}
)
public class GetLinkableIssueDetailRequest {

    /** 源领用单号 originalBusNo。 */
    @NotBlank(message = "originalBusNo 不能为空")
    @Schema(description = "源领用单号 originalBusNo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String originalBusNo;

    /** 科室药房 purposeLocationId。 */
    @NotNull(message = "purposeLocationId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "科室药房 purposeLocationId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

    /** 排除退领单号 excludeReturnBusNo。 */
    @Schema(description = "排除退领单号 excludeReturnBusNo")
    private String excludeReturnBusNo;
}
