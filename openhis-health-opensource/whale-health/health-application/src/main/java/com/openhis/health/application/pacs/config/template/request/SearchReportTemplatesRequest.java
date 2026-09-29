/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.config.template.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报告模板列表请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "报告模板-列表请求")
public class SearchReportTemplatesRequest extends PageQueryBinder.AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    /** 模态码。 */
    @Schema(description = "模态码")
    private String modalityCode;

    /** 仅启用。 */
    @Schema(description = "仅启用")
    private Boolean enabledOnly;
}
