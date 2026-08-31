/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.prescriptionreview.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.whale.core.application.dto.ExcelExportColumnDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 导出点评记录请求（IF-PRV-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "导出点评记录请求", requiredProperties = {"exportColumns"})
public class ExportReviewsRequest {

    /** 点评状态：PENDING/REVIEWED；空=不限。 */
    @Schema(description = "点评状态 code")
    private String statusEnum;

    /** 点评日起（yyyy-MM-dd）；空=不限。 */
    @Schema(description = "点评日起（yyyy-MM-dd）")
    private String reviewedDateFrom;

    /** 点评日止（yyyy-MM-dd）；空=不限。 */
    @Schema(description = "点评日止（yyyy-MM-dd）")
    private String reviewedDateTo;

    /** 是否合理；空=不限。 */
    @Schema(description = "是否合理")
    private Boolean reasonableFlag;

    /** 点评人主键；空=不限。 */
    @Schema(description = "点评人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reviewerId;

    /** 处方号模糊关键字。 */
    @Schema(description = "处方号关键字")
    private String prescriptionNoKeyword;

    /** 业务号模糊关键字。 */
    @Schema(description = "业务号关键字")
    private String busNoKeyword;

    /**
     * 导出列清单（与发退药记录导出同构；{@code field} 须落在 ExportRowDto {@code @Excel} 白名单）。
     */
    @Schema(description = "导出列清单", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ExcelExportColumnDto> exportColumns;
}
