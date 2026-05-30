/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.emergency.emgtriage.station.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.emergency.emgtriage.station.dto.SaveEmgTriageAssessmentItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 保存或提交急诊分诊（IF-EMG-06）请求体。
 * 与 {@link com.openhis.health.domain.emergency.emgtriage.command.SaveEmgTriageCommand} 对齐但<strong>不包含</strong>
 * {@code triageStatusEnum}，有效态由领域在保存时默认 {@code ACTIVE}。
 * {@code isStaged=true} 时写入草稿态（仍 {@code ACTIVE}），服务端补齐必填缺省项；正式提交置 {@code false}。
 * {@code priorEmgTriageId} 仅用于详情回显或 IF-EMG-09 二次分诊提交，IF-EMG-06 保存/修订须为空。
 */
@Data
@Accessors(chain = true)
@Schema(description = "急诊分诊工作站-保存或提交分诊请求")
public class SaveOrSubmitEmgTriageRequest {

    /** 分诊主键，空表示新建。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "分诊主键，空表示新建")
    private Long id;

    /**
     * 前序有效分诊主键：IF-EMG-09 二次分诊提交时必填；详情回显时表示当前行为取代后新建行；IF-EMG-06 修订须为空。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "前序有效分诊主键（二次分诊或详情回显；普通保存须为空）")
    private Long priorEmgTriageId;

    /** 就诊标识；新建分诊且尚未开立急诊就诊时可为空，由服务端 IF-EMG-06 编排补全。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识（可选，缺省时由服务端开立或复用急诊就诊）")
    private Long encounterId;

    @NotNull
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "分诊确认时间（暂存时可为空，由服务端默认当前时刻）")
    private LocalDateTime triageTime;

    @Schema(description = "主诉 JSON")
    private String chiefComplaintJson;

    @Schema(description = "症状 JSON")
    private String symptomJson;

    @Schema(description = "最终分级 1～5（暂存时可为空，默认 III 级）")
    private Integer triageLevelEnum;

    @Schema(description = "建议分级")
    private Integer suggestedLevelEnum;

    @Schema(description = "覆盖建议原因")
    private String levelAdjustReason;

    @Schema(description = "变更原因编码（触及分级/去向时必填）")
    private String changeReasonCode;

    @Schema(description = "变更备注")
    private String changeRemark;

    @NotNull
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "挂号科室", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long organizationId;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "主去向位置（暂存时可为空或 0，由服务端按机构补第一条启用位置）")
    private Long locationId;

    @Schema(description = "指派医生快照")
    private String assignedDoctorSnapshot;

    @Schema(description = "病种分类 JSON")
    private String diseaseTypesJson;

    @Schema(description = "患者来源字典码")
    private String patientSource;

    /** 展示伴生文本（可选回填）。 */
    @Schema(description = "患者来源展示文本")
    private String patientSourceDictText;

    @Schema(description = "是否绿通（暂存可空，默认 false）")
    private Boolean isGreenChannel;

    @Schema(description = "是否免费挂号（暂存可空，默认 false）")
    private Boolean isFreeRegister;

    @Schema(description = "队列优先级（暂存可空，默认 50）")
    private Integer queuePriority;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "分诊挂号协同模式")
    private String triageRegistrationModeEnum;

    @NotNull
    @Schema(description = "是否暂存", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean isStaged;

    @Schema(description = "暂存序号")
    private Integer stageQueueSeq;

    @Schema(description = "三无预分诊号")
    private String provisionalTriageNo;

    @Schema(description = "特殊人群标签 JSON")
    private String specialPopulationTagsJson;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "群伤事件标识")
    private Long mciEventId;

    @Schema(description = "黑名单警示已确认")
    private Boolean blacklistAlertAck;

    @Schema(description = "来院方式编码")
    private String arrivalModeEnum;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "外院转入机构")
    private Long transferInOrgId;

    @Schema(description = "去向模式 SINGLE/MULTI（暂存可空，默认 SINGLE）")
    private String destinationModeEnum;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "次要去向")
    private Long secondaryLocationId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "绿通发病时间")
    private LocalDateTime greenChannelOnsetTime;

    @Schema(description = "流调 JSON")
    private String epidemicSurveyJson;

    @Schema(description = "专病绿通编码")
    private String specialtyGreenChannelCode;

    @Schema(description = "120 JSON")
    private String ambulance120Json;

    @Schema(description = "110 JSON")
    private String police110Json;

    @Schema(description = "监护仪绑定 JSON")
    private String monitorDeviceBindingJson;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "腕带打印时间")
    private LocalDateTime wristbandPrintedAt;

    @Schema(description = "体征自动分级策略键")
    private String vitalsAutoTierPolicyId;

    @Schema(description = "机构允许无主诉空载荷")
    private Boolean chiefComplaintOptional;

    @Schema(description = "评估子项（全量对齐）")
    private List<SaveEmgTriageAssessmentItemDto> assessmentItems = new ArrayList<>();
}
