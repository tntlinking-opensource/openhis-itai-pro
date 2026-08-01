/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissue.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inventory.department.deptissue.dto.DeptIssueLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 编辑科室领用单请求（IF-DEP-IS-05）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(
    description = "编辑科室领用单 IF-DEP-IS-05",
    requiredProperties = {"busNo", "purposeLocationId", "consumingOrganizationId", "lines"}
)
public class UpdateDeptIssueRequest {

    @NotBlank(message = "busNo 不能为空")
    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    @NotNull(message = "purposeLocationId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 科室药房 purposeLocationId。 */
    @Schema(description = "科室药房 purposeLocationId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

    @NotNull(message = "consumingOrganizationId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 领用科室行政 Organization 主键（与科室药房解耦，手选落库）。 */
    @Schema(description = "领用科室 consumingOrganizationId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long consumingOrganizationId;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 经办人 practitionerId。 */
    @Schema(description = "经办人 practitionerId")
    private Long practitionerId;

    /** 领用原因 reason。 */
    @Schema(description = "领用原因 reason")
    private String reason;

    /** 备注 remake。 */
    @Schema(description = "备注 remake")
    private String remake;

    @NotEmpty(message = "明细不能为空")
    /** 明细行 lines。 */
    @Schema(description = "明细行 lines", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<DeptIssueLineDto> lines;
}
