/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.inoutdetail.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientInOutDetailReportConstants;
import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 住院患者入出明细报表页初始化响应（IF-RPT-IOE-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院患者入出明细报表页初始化响应")
public class LoadInpatientInOutDetailReportPageInitResponse {

    /** 默认日起（今日往前 1 个自然月 00:00:00）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "默认开始时间")
    private LocalDateTime defaultDateFrom;

    /** 默认日止（当日 23:59:59）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "默认结束时间")
    private LocalDateTime defaultDateTo;

    /**
     * 默认入出状态：{@link InpatientInOutDetailReportConstants#MOVEMENT_STATUS_ALL}。
     */
    @Schema(description = "默认入出状态")
    private String defaultMovementStatus;

    /** 默认页大小。 */
    @Schema(description = "默认页大小")
    private Integer defaultPageSize;

    /** 入出状态下拉。 */
    @Schema(description = "入出状态下拉")
    private List<OptionDto> movementStatusOptions = new ArrayList<>();

    /** 入院科室下拉。 */
    @Schema(description = "入院科室下拉")
    private List<OptionDto> organizationOptions = new ArrayList<>();

    /** 病区下拉；初始为空，选科室后走 IF-01a。 */
    @Schema(description = "病区下拉（初始空）")
    private List<OptionDto> wardOptions = new ArrayList<>();
}
