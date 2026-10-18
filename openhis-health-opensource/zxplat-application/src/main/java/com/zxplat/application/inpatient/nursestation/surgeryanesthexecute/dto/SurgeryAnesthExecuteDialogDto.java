/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.surgeryanesthexecute.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 手术/麻醉执行弹窗装载 DTO（IF-IP-SAE-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "手术/麻醉执行弹窗")
public class SurgeryAnesthExecuteDialogDto {

    /** 服务申请主键。 */
    @Schema(description = "服务申请主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long serviceRequestId;

    /**
     * 类目：24=手术 / 25=麻醉（ServiceRequest.category_enum）。
     */
    @Schema(description = "类目 code：24/25")
    private String categoryEnum;

    /** 项目名称（ActivityDefinition.name）。 */
    @Schema(description = "项目名称")
    private String activityName;

    /**
     * 手术时间默认值（SR.procedureTime；护士可改）。
     */
    @Schema(description = "手术时间（默认同申请）")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime procedureTime;

    /** 样本（手术弹窗；默认可空）。 */
    @Schema(description = "样本")
    private String specimen;

    /** 备注（手术弹窗）。 */
    @Schema(description = "备注")
    private String note;

    /** 配合护士主键（可编）。 */
    @Schema(description = "配合护士主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long nurseId;

    /** 配合护士姓名。 */
    @Schema(description = "配合护士姓名")
    private String nurseName;

    /** 麻醉开始（仅麻醉弹窗；默认可空）。 */
    @Schema(description = "麻醉开始时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime anesthesiaStartTime;

    /** 麻醉结束（仅麻醉弹窗）。 */
    @Schema(description = "麻醉结束时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime anesthesiaEndTime;

    /** 主手术标志（只读展示）。 */
    @Schema(description = "主手术标志")
    private Boolean mainProcedureFlag;

    /** 手术级别（只读）。 */
    @Schema(description = "手术级别字典码")
    private String procedureLevelCode;

    /** 麻醉方法（只读）。 */
    @Schema(description = "麻醉方法字典码")
    private String anesthesiaMethodCode;

    /** 切口类别（只读；申请预填）。 */
    @Schema(description = "切口类别字典码")
    private String incisionTypeCode;

    /** 主刀医师主键（只读）。 */
    @Schema(description = "主刀医师主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long surgeonId;

    /** 主刀医师姓名（只读）。 */
    @Schema(description = "主刀医师姓名")
    private String surgeonName;

    /** 一助主键（只读）。 */
    @Schema(description = "一助主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long firstAssistantId;

    /** 一助姓名（只读）。 */
    @Schema(description = "一助姓名")
    private String firstAssistantName;

    /** 二助主键（只读）。 */
    @Schema(description = "二助主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long secondAssistantId;

    /** 二助姓名（只读）。 */
    @Schema(description = "二助姓名")
    private String secondAssistantName;

    /** 麻醉医师主键（只读）。 */
    @Schema(description = "麻醉医师主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long anesthetistId;

    /** 麻醉医师姓名（只读）。 */
    @Schema(description = "麻醉医师姓名")
    private String anesthetistName;

    /**
     * 计划槽位时刻（occurrence 将用此值；与 procedureTime 分离）。
     */
    @Schema(description = "计划执行槽位时刻")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime planTime;
}
