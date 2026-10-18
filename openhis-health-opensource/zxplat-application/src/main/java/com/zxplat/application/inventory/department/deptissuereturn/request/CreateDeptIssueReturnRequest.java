/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.deptissuereturn.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.inventory.department.deptissuereturn.dto.DeptIssueReturnLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 新建科室退领单请求（IF-DEP-TL-04）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(
    description = "新建科室退领单 IF-DEP-TL-04",
    requiredProperties = {"originalBusNo", "purposeLocationId", "lines"}
)
public class CreateDeptIssueReturnRequest {

    /** 源领用单号 originalBusNo。 */
    @NotBlank(message = "originalBusNo 不能为空")
    @Schema(description = "源领用单号 originalBusNo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String originalBusNo;

    /** 科室药房 purposeLocationId。 */
    @NotNull(message = "purposeLocationId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "科室药房 purposeLocationId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

    /** 明细行 lines。 */
    @NotEmpty(message = "明细不能为空")
    @Schema(description = "明细行 lines", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<DeptIssueReturnLineDto> lines;
}
