/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.surplusretention.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 药品结余留用报表明细分页请求（IF-RPT-SRB-02）。
 */
@Data
@Schema(description = "药品结余留用报表明细分页请求")
public class PageSurplusRetentionReportRequest {

    /**
     * 结余留用批次号（术语 {@code surplus_retention_batch_code}）；必选。
     */
    @Schema(description = "结余留用批次号")
    private String surplusRetentionBatchCode;

    /**
     * 签发时间窗起（{@code yyyy-MM-dd}）；日初 00:00:00，对齐 {@code req_authored_time}。
     */
    @Schema(description = "签发时间窗起")
    private String authoredDateFrom;

    /**
     * 签发时间窗止（{@code yyyy-MM-dd}）；日末 23:59:59。
     */
    @Schema(description = "签发时间窗止")
    private String authoredDateTo;

    /** 页码（1-based；非法由 AppService Gate 拒绝）。 */
    @Schema(description = "页码")
    private Integer pageNo;

    /** 页大小（空则回落默认；超过 MAX_PAGE_SIZE 由 Gate 截断）。 */
    @Schema(description = "页大小")
    private Integer pageSize;
}
