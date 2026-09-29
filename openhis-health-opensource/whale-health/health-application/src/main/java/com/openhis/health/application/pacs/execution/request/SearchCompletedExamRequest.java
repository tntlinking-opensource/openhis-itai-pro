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
 * 已检列表请求（IF-PACS-EXE-02）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "检查执行-已检列表请求")
public class SearchCompletedExamRequest extends PageQueryBinder.AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    @Schema(description = "科室")
    private Long organizationId;

    @Schema(description = "模态码")
    private String modalityCode;
}
