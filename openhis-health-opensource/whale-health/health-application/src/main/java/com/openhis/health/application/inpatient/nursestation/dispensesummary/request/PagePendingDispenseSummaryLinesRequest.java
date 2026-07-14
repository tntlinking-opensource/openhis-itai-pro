/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.dispensesummary.request;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分页查询待汇总明细请求（IF-IP-SUM-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "分页查询待汇总明细 IF-IP-SUM-01", requiredProperties = {
    "encounterIds", "medicineCategoryTab", "therapyFilter", "pageNo", "pageSize"
})
public class PagePendingDispenseSummaryLinesRequest extends AbstractHealthPageRequest {

    /** 左侧多选就诊主键（BR-IP-SUM-001）。 */
    @NotEmpty(message = "请选择至少一名患者")
    @Schema(description = "左侧多选就诊主键", required = true)
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> encounterIds;

    /** 中西药 Tab：west / tcm。 */
    @NotNull(message = "药品分类 Tab 不能为空")
    @Schema(description = "中西药 Tab：west / tcm", required = true)
    private String medicineCategoryTab;

    /** 长临筛选：all / long / temp。 */
    @NotNull(message = "长临筛选不能为空")
    @Schema(description = "长临筛选：all / long / temp", required = true)
    private String therapyFilter;

    /** 计划发药截止时间。 */
    @Schema(description = "计划发药截止时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime queryDeadline;
}
