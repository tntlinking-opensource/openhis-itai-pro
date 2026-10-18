/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.inoutdetail.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.InpatientInOutDetailReportConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 住院患者入出明细报表明细分页请求（IF-RPT-IOE-02）。
 */
@Data
@Accessors(chain = true)
@Schema(
    description = "住院患者入出明细报表明细分页请求",
    requiredProperties = {"dateFrom", "dateTo", "movementStatus", "pageNo", "pageSize"})
public class PageInpatientInOutDetailReportRequest {

    /** 业务时间窗起（含）。 */
    @NotNull(message = "开始时间不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime dateFrom;

    /** 业务时间窗止（含）。 */
    @NotNull(message = "结束时间不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "结束时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime dateTo;

    /**
     * 入出状态：{@link InpatientInOutDetailReportConstants#MOVEMENT_STATUS_ALL} 等。
     */
    @NotNull(message = "入出状态不能为空")
    @Schema(description = "入出状态 ALL/ADMISSION/DISCHARGE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String movementStatus;

    /** 入院科室主键；空=不限。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "入院科室主键")
    private Long organizationId;

    /** 入院病区位置主键；空=不限（已选科室时含该科全部病区）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "入院病区位置主键")
    private Long wardLocationId;

    /** 页码（1-based）。 */
    @NotNull(message = "页码不能为空")
    @Schema(description = "页码", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer pageNo;

    /** 页大小。 */
    @NotNull(message = "页大小不能为空")
    @Schema(description = "页大小", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer pageSize;
}
