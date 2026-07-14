/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissuereturn.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 可关联领用单分页请求（IF-DEP-TL-12）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(
    description = "可关联领用单分页 IF-DEP-TL-12",
    requiredProperties = {"purposeLocationId", "pageNo", "pageSize"}
)
public class PageLinkableIssueDocumentsRequest extends AbstractHealthPageRequest {

    /** 科室药房 purposeLocationId。 */
    @NotNull(message = "purposeLocationId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "科室药房 purposeLocationId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;
}
