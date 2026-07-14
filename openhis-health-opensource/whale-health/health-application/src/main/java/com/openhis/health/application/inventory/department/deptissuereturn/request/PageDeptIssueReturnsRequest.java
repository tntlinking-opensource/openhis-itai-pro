/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissuereturn.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 科室退领单分页列表请求（IF-DEP-TL-02）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(
    description = "科室退领单分页列表 IF-DEP-TL-02",
    requiredProperties = {"purposeLocationId", "pageNo", "pageSize"}
)
public class PageDeptIssueReturnsRequest extends AbstractHealthPageRequest {

    /** 科室药房 purposeLocationId，0 表示全部可见科室药房。 */
    @NotNull(message = "purposeLocationId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "科室药房 purposeLocationId，0 表示全部可见科室药房", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

    /** 单据状态 statusEnum。 */
    @Schema(description = "单据状态 statusEnum")
    private String statusEnum;

    /** 申请时间起 applyTimeFrom。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "申请时间起 applyTimeFrom")
    private LocalDateTime applyTimeFrom;

    /** 申请时间止 applyTimeTo。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "申请时间止 applyTimeTo")
    private LocalDateTime applyTimeTo;
}
