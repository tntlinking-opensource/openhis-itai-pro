/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.prescriptionreview.dto;

import com.openhis.whale.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 点评记录导出行（IF-PRV-03；@Excel 白名单与列表列对齐）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "点评记录导出行")
public class PrescriptionReviewExportRowDto {

    /** 处方号。 */
    @Excel(name = "处方号", sort = 1)
    @Schema(description = "处方号")
    private String prescriptionNo;

    /** 点评业务号（PRV 日序）。 */
    @Excel(name = "业务号", sort = 2)
    @Schema(description = "业务号")
    private String busNo;

    /** 点评状态展示。 */
    @Excel(name = "点评状态", sort = 3)
    @Schema(description = "点评状态")
    private String statusText;

    /** 是否合理展示。 */
    @Excel(name = "是否合理", sort = 4)
    @Schema(description = "是否合理")
    private String reasonableText;

    /** 原因。 */
    @Excel(name = "原因", sort = 5)
    @Schema(description = "原因")
    private String reason;

    /** 备注。 */
    @Excel(name = "备注", sort = 6)
    @Schema(description = "备注")
    private String remark;

    /** 点评人姓名。 */
    @Excel(name = "点评人", sort = 7)
    @Schema(description = "点评人")
    private String reviewerName;

    /** 点评时间。 */
    @Excel(name = "点评时间", sort = 8)
    @Schema(description = "点评时间")
    private String reviewedAt;
}
