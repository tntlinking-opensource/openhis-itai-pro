/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.prescriptionspotcheck.response;

import com.openhis.health.application.report.prescriptionspotcheck.dto.PrescriptionSpotCheckHerbalRowDto;
import com.openhis.health.application.report.prescriptionspotcheck.dto.PrescriptionSpotCheckWesternRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 门诊处方抽查明细分页响应（IF-RPT-PSC-02）。
 *
 * <p>{@code westernContent} 与 {@code herbalContent} 按 {@link #reportType} 互斥填充；
 * 分页元数据走 {@link PagedResult}（{@code content} 留空列表）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "门诊处方抽查明细分页响应")
public class PagePrescriptionSpotCheckReportResponse extends PagedResult<Object> {

    /** 当前查询报表类型。 */
    @Schema(description = "报表类型")
    private String reportType;

    /** 西药及中成药行；类型非西药时为空列表。 */
    @Schema(description = "西药及中成药行")
    private List<PrescriptionSpotCheckWesternRowDto> westernContent;

    /** 中药饮片行；类型非饮片时为空列表。 */
    @Schema(description = "中药饮片行")
    private List<PrescriptionSpotCheckHerbalRowDto> herbalContent;
}
