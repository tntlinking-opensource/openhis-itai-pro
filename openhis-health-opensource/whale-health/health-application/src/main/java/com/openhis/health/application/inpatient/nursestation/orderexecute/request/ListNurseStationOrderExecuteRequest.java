/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderexecute.request;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 医嘱执行列表查询请求（IF-IP-EXE-01）。
 *
 * <p>护士左侧多选患者后加载执行列表；{@code executionDeadline} 驱动长期频次展开上界，
 * {@code executeTab} 收窄当前 Tab 计划点，{@code therapyType} 可选收窄长临。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱执行列表查询请求", requiredProperties = {"encounterIds", "executeTab", "executionDeadline"})
public class ListNurseStationOrderExecuteRequest {

    /** 左侧多选就诊主键。 */
    @NotEmpty(message = "请选择至少一名患者")
    @Schema(description = "左侧多选就诊主键", required = true)
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> encounterIds;

    /** 执行 Tab：pending / executed / skipped / cancelled（BR-IP-EXE-010，必填）。 */
    @NotNull(message = "执行 Tab 不能为空")
    /** 执行 Tab：pending / executed / skipped / cancelled。 */
    @Schema(description = "执行 Tab：pending / executed / skipped / cancelled", required = true)
    private String executeTab;

    /** 长临筛选：all / long / temp。 */
    @Schema(description = "长临筛选：all / long / temp")
    private String therapyType;

    /** 截止时间（频次展开与 Tab 过滤上界，BR-IP-EXE-004/010）。 */
    @NotNull(message = "截止时间不能为空")
    /** 截止时间。 */
    @Schema(description = "截止时间", required = true)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime executionDeadline;

    /** 中西药 Tab：west / tcm；默认 west。 */
    @Schema(description = "中西药 Tab：west / tcm；默认 west")
    private String medicineCategoryTab;
}
