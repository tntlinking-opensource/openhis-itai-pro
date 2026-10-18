/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.prescriptionspotcheck.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 门诊处方抽查明细分页请求（IF-RPT-PSC-02）。
 */
@Data
@Schema(description = "门诊处方抽查明细分页请求")
public class PagePrescriptionSpotCheckReportRequest {

    /**
     * 报表类型：{@code WESTERN_PATENT} / {@code HERBAL}；必选。
     */
    @Schema(description = "报表类型")
    private String reportType;

    /**
     * 开方时间窗起（{@code yyyy-MM-dd}）；日初 00:00:00。
     */
    @Schema(description = "开方时间窗起")
    private String openedDateFrom;

    /**
     * 开方时间窗止（{@code yyyy-MM-dd}）；日末 23:59:59。
     */
    @Schema(description = "开方时间窗止")
    private String openedDateTo;

    /** 页码（1-based）。 */
    @Schema(description = "页码")
    private Integer pageNo;

    /** 页大小。 */
    @Schema(description = "页大小")
    private Integer pageSize;
}
