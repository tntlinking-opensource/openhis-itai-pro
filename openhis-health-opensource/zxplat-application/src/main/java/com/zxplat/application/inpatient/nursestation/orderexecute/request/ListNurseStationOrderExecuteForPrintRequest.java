/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.orderexecute.request;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
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
 * 打印中心 · 医嘱执行列表查询请求（与 IF-IP-EXE-01 隔离）。
 *
 * <p>供住院护士站打印中心拉取输液贴/巡视卡/用药单/检查检验/采血贴等数据源；
 * 在 EXE-01 字段基础上增加计划点下界与开嘱日区间，互不影响医嘱执行页契约。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "打印中心医嘱执行列表查询请求", requiredProperties = {"encounterIds", "executeTab", "executionDeadline"})
public class ListNurseStationOrderExecuteForPrintRequest {

    /** 左侧多选就诊主键。 */
    @NotEmpty(message = "请选择至少一名患者")
    @Schema(description = "左侧多选就诊主键", required = true)
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> encounterIds;

    /** 执行 Tab：pending / executed / skipped / cancelled。 */
    @NotNull(message = "执行 Tab 不能为空")
    @Schema(description = "执行 Tab：pending / executed / skipped / cancelled", required = true)
    private String executeTab;

    /** 长临筛选：all / long / temp。 */
    @Schema(description = "长临筛选：all / long / temp")
    private String therapyType;

    /** 截止时间（频次展开与 Tab 过滤上界）。 */
    @NotNull(message = "截止时间不能为空")
    @Schema(description = "截止时间", required = true)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime executionDeadline;

    /**
     * 计划执行日起（含）：按槽位 {@code planTime} 收窄；空表示不下界过滤。
     *
     * <p>打印中心按日期范围查询时传入区间日 00:00:00，与 {@code executionDeadline} 组成闭区间。</p>
     */
    @Schema(description = "计划执行日起（含），空表示不下界过滤")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime planTimeFrom;

    /**
     * 开嘱日起（含）：按行 {@code reqOpenedTime}（空回落签发时刻）收窄；空表示不下界过滤。
     *
     * <p>检查/检验/采血贴按开嘱日查询时传入区间日起 00:00:00。</p>
     */
    @Schema(description = "开嘱日起（含），空表示不下界过滤")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime reqOpenedTimeFrom;

    /**
     * 开嘱日止（含）：按行 {@code reqOpenedTime}（空回落签发时刻）收窄；空表示不上界过滤。
     *
     * <p>与 {@code reqOpenedTimeFrom} 同时传时须 from ≤ to。</p>
     */
    @Schema(description = "开嘱日止（含），空表示不上界过滤")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime reqOpenedTimeTo;

    /** 中西药 Tab：west / tcm；默认 west。 */
    @Schema(description = "中西药 Tab：west / tcm；默认 west")
    private String medicineCategoryTab;

    /**
     * 执行记录历史模式（医生站/病案「执行记录」只读弹窗）。
     *
     * <p>true：按 Procedure 事实行分桶（已被 CANCEL 对冲的 COMPLETED 仍进已执行），
     * 不做护士站净态折叠；false/空：护士站操作列表净态口径（默认）。</p>
     */
    @Schema(description = "执行记录历史模式：true=事实行（含对冲前 COMPLETED）；默认 false")
    private Boolean executionRecordHistory;
}
