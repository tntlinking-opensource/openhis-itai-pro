/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.report.audit.request;

import com.zxplat.framework.core.application.paging.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 待审列表请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "报告审核-待审列表请求")
public class SearchPendingAuditRequest extends AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    @Schema(description = "科室")
    private Long organizationId;
}
