/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissue.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inventory.department.deptissue.dto.DeptIssueLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 新建科室领用单请求（IF-DEP-IS-04）。
 *
 * <p>{@code practitionerId} 为经办人医务人员主键，可空；{@code sourceLocationId} 禁止传入（DEP-IS-SDD-R-002）。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(
    description = "新建科室领用单 IF-DEP-IS-04",
    requiredProperties = {"purposeLocationId", "lines"}
)
public class CreateDeptIssueRequest {

    @NotNull(message = "purposeLocationId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 科室药房 purposeLocationId。 */
    @Schema(description = "科室药房 purposeLocationId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

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
