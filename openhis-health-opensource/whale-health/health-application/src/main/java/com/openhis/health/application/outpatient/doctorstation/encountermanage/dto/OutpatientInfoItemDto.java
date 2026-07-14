/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.encountermanage.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 医生站门诊信息列表行 DTO（与 OutpatientInfo 读模型展示字段对齐）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-门诊信息列表行")
public class OutpatientInfoItemDto {

    /** 就诊主键（列表锚点）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键（列表锚点）")
    private Long encounterId;

    /** 就诊业务号。 */
    @Schema(description = "就诊业务号")
    private String encounterBusNo;

    /** 就诊类别整型展示（来源 class_enum 映射）。 */
    @Schema(description = "就诊类别整型展示（来源 class_enum 映射）")
    private Integer classEnum;

    /** 就诊状态 code。 */
    @Schema(description = "就诊状态 code")
    private String statusEnum;

    /** 就诊状态展示文本。 */
    @Schema(description = "就诊状态展示文本")
    private String statusEnumText;

    /** 患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者主键。 */
    @Schema(description = "患者主键")
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 性别编码（AdministrativeGender：MALE/FEMALE/UNKNOWN）。 */
    @Schema(description = "性别编码（AdministrativeGender：MALE/FEMALE/UNKNOWN）")
    private String genderEnum;

    /** 性别展示文本。 */
    @Schema(description = "性别展示文本")
    private String genderEnumText;

    /** 出生日期。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 出生日期。 */
    @Schema(description = "出生日期")
    private LocalDateTime birthDate;

    /** 年龄展示文本。 */
    @Schema(description = "年龄展示文本")
    private String ageText;

    /** 证件号。 */
    @Schema(description = "证件号")
    private String idCard;

    /** 联系电话。 */
    @Schema(description = "联系电话")
    private String phone;

    /** 科室主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 科室主键。 */
    @Schema(description = "科室主键")
    private Long organizationId;

    /** 科室名称。 */
    @Schema(description = "科室名称")
    private String organizationName;

    /** 接诊医生主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 接诊医生主键。 */
    @Schema(description = "接诊医生主键")
    private Long doctorId;

    /** 接诊医生姓名。 */
    @Schema(description = "接诊医生姓名")
    private String doctorName;

    /** 服务类型主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 服务类型主键。 */
    @Schema(description = "服务类型主键")
    private Long serviceTypeId;

    /** 服务类型名称。 */
    @Schema(description = "服务类型名称")
    private String serviceTypeName;

    /** 就诊开始时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 就诊开始时间（挂号/预约）。 */
    @Schema(description = "就诊开始时间（挂号/预约）")
    private LocalDateTime startTime;

    /** 接诊时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 接诊时间。 */
    @Schema(description = "接诊时间")
    private LocalDateTime receptionTime;

    /** 结束时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 就诊结束时间。 */
    @Schema(description = "就诊结束时间")
    private LocalDateTime endTime;

    /** 当日展示序号。 */
    @Schema(description = "当日展示序号")
    private Integer displayOrder;

    /** 初诊/复诊展示文本。 */
    @Schema(description = "初诊/复诊展示文本")
    private String firstEnumText;

    /** 门诊费用总额。 */
    @Schema(description = "门诊费用总额")
    private BigDecimal totalAmount;

    /** 已支付金额。 */
    @Schema(description = "已支付金额")
    private BigDecimal paidAmount;

    /** 已退款金额。 */
    @Schema(description = "已退款金额")
    private BigDecimal refundAmount;

    /** 诊断列表。 */
    @Schema(description = "诊断列表")
    private List<EncounterDiagnosisRowDto> diagnoses;

    /** 分诊级别（可能由字符串码映射为整数展示）。 */
    @Schema(description = "分诊级别（可能由字符串码映射为整数展示）")
    private Integer triageLevelEnum;

    /** 分诊时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 分诊时间。 */
    @Schema(description = "分诊时间")
    private LocalDateTime triageTime;

    /** 是否工伤就诊。 */
    @Schema(description = "是否工伤就诊")
    private Boolean workInjuryEncounter;

    /** 工伤审批态摘要。 */
    @Schema(description = "工伤审批态摘要")
    private String workInjuryApprovalStatusLabel;

    /** 工伤契约 id。 */
    @Schema(description = "工伤契约 id")
    private Long workInjuryContractId;

    /** 工伤审批是否有效。 */
    @Schema(description = "工伤审批是否有效")
    private Boolean workInjuryQualificationValid;

    /** 是否建议医生站发起 2301。 */
    @Schema(description = "是否建议医生站发起 2301")
    private Boolean workInjuryNeeds2301Upload;
}
