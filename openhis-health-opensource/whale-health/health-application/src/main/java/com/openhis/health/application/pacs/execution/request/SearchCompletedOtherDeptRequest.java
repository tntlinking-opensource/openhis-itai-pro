/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.execution.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 他科已检列表请求（IF-PACS-EXE-03）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "检查执行-他科已检列表请求")
public class SearchCompletedOtherDeptRequest extends PageQueryBinder.AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    @Schema(description = "排除科室（当前科室，必填）")
    private Long excludeOrganizationId;

    @Schema(description = "模态码")
    private String modalityCode;
}
