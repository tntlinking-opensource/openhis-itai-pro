/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.report.query.request;

import com.zxplat.framework.core.application.paging.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 报告列表查询请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "报告查询-列表请求")
public class SearchReportsRequest extends AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    @Schema(description = "报告状态")
    private String statusEnum;

    @Schema(description = "科室")
    private Long organizationId;

    @Schema(description = "患者主键")
    private Long patientId;

    @Schema(description = "签发起（含）")
    private LocalDateTime issuedFrom;

    @Schema(description = "签发止（含）")
    private LocalDateTime issuedTo;
}
